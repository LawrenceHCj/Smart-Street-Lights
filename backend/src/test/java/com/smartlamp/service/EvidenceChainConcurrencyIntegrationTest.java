package com.smartlamp.service;

import com.smartlamp.entity.EvidenceChainEntry;
import com.smartlamp.entity.EvidenceChainHead;
import com.smartlamp.repository.EvidenceChainEntryRepository;
import com.smartlamp.repository.EvidenceChainHeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL 并发集成测试：验证同一 device 首次并发 append 时，
 * INSERT IGNORE 创建唯一链头 + SELECT ... FOR UPDATE（PESSIMISTIC_WRITE）串行化，
 * 最终 seq 必须为 1、2，不重复，ChainHead 指向 seq2。
 *
 * 该假设 Mockito 无法验证，必须用真实数据库锁/事务行为证明。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(EvidenceChainService.class)
@TestPropertySource(properties = {
        "evidence.chain.enabled=true",
        "evidence.chain.hmac.current-key-id=v1",
        "evidence.chain.hmac.secret=test-secret",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EvidenceChainConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private EvidenceChainService service;

    @Autowired
    private EvidenceChainEntryRepository entryRepository;

    @Autowired
    private EvidenceChainHeadRepository headRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstAppendProducesSequentialSeq() throws Exception {
        // 多轮，每轮用新的随机 deviceCode，提高触发竞争条件的概率
        for (int round = 0; round < 10; round++) {
            String deviceCode = "CONC-" + System.nanoTime();
            runConcurrentAppend(deviceCode);
        }
    }

    private void runConcurrentAppend(String deviceCode) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    service.append(deviceCode, EvidenceChainService.EVENT_TELEMETRY,
                            System.currentTimeMillis(), EvidenceChainService.SOURCE_LIGHT_POINT,
                            1L, "{\"deviceCode\":\"" + deviceCode + "\",\"lux\":85.0,\"lampStatus\":\"OFF\"}");
                    return null;
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS); // 超时即失败，避免死锁挂起
            }

            List<EvidenceChainEntry> entries = entryRepository
                    .findByDeviceCodeAndSeqGreaterThanOrderBySeqAsc(deviceCode, 0L, PageRequest.of(0, 10));

            assertThat(entries).as("entry 数量").hasSize(2);
            assertThat(entries.get(0).getSeq()).as("seq1").isEqualTo(1L);
            assertThat(entries.get(1).getSeq()).as("seq2").isEqualTo(2L);
            assertThat(entries.get(1).getPrevHash()).as("prevHash").isEqualTo(entries.get(0).getEntryHash());
            assertThat(entries.get(0).getEntryMac()).as("entry1 mac").isNotBlank();
            assertThat(entries.get(1).getEntryMac()).as("entry2 mac").isNotBlank();

            EvidenceChainHead head = headRepository.findById(deviceCode).orElseThrow();
            assertThat(head.getLatestSeq()).as("head seq").isEqualTo(2L);
            assertThat(head.getLatestHash()).as("head hash").isEqualTo(entries.get(1).getEntryHash());
            assertThat(head.getHeadMac()).as("head mac").isNotBlank();
        } finally {
            pool.shutdownNow();
        }
    }
}
