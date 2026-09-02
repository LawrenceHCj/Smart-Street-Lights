package com.smartlamp.repository;

import com.smartlamp.entity.EvidenceChainHead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface EvidenceChainHeadRepository extends JpaRepository<EvidenceChainHead, String> {

    /** 悲观锁定位链头行，串行化同一设备的并发追加。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from EvidenceChainHead h where h.deviceCode = :deviceCode")
    Optional<EvidenceChainHead> findForUpdate(@Param("deviceCode") String deviceCode);

    /** 链头不存在则插入（并发首次创建不抛唯一约束异常，避免外层事务 rollback-only）。 */
    @Modifying
    @Query(value = "INSERT IGNORE INTO evidence_chain_head (device_code, latest_seq, latest_hash) VALUES (:deviceCode, 0, '')",
            nativeQuery = true)
    void insertHeadIfAbsent(@Param("deviceCode") String deviceCode);
}
