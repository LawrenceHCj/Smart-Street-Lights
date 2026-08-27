package com.smartlamp.task;

import com.smartlamp.config.RetentionProperties;
import com.smartlamp.repository.LightPointRepository;
import com.smartlamp.repository.MqttDeadLetterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class DataRetentionTask {
    private static final Logger log = LoggerFactory.getLogger(DataRetentionTask.class);

    @Autowired
    private LightPointRepository lightPointRepository;

    @Autowired
    private MqttDeadLetterRepository mqttDeadLetterRepository;

    @Autowired
    private RetentionProperties retentionProperties;

    // 每天凌晨 3 点执行
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanExpiredData() {
        // 清理光照历史
        long lightPointCutoff = System.currentTimeMillis() - (retentionProperties.getLightPointDays() * 24L * 3600 * 1000);
        lightPointRepository.deleteByTsBefore(lightPointCutoff);

        // 清理死信数据
        LocalDateTime deadLetterCutoff = LocalDateTime.now().minusDays(retentionProperties.getDeadLetterDays());
        mqttDeadLetterRepository.deleteByReceivedAtBefore(deadLetterCutoff);

        log.info("数据保留清理任务完成: telemetryDays={}, deadLetterDays={}",
                retentionProperties.getLightPointDays(), retentionProperties.getDeadLetterDays());
    }
}
