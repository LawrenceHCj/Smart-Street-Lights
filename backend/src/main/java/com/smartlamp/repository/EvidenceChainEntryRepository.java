package com.smartlamp.repository;

import com.smartlamp.entity.EvidenceChainEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EvidenceChainEntryRepository extends JpaRepository<EvidenceChainEntry, Long>,
        JpaSpecificationExecutor<EvidenceChainEntry> {
    /** 按 seq 分页游标读取，避免一次加载整条链占用内存。 */
    List<EvidenceChainEntry> findByDeviceCodeAndSeqGreaterThanOrderBySeqAsc(String deviceCode, long seq, Pageable pageable);

    /** 锚点绑定主链：按 deviceCode + seq 精确定位锚点对应的证据条目。 */
    Optional<EvidenceChainEntry> findByDeviceCodeAndSeq(String deviceCode, long seq);

    /** 是否存在该设备的证据（轻量判断，不扫描全链）。 */
    boolean existsByDeviceCode(String deviceCode);
}
