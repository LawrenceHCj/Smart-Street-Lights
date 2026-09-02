package com.smartlamp.repository;

import com.smartlamp.entity.DataIntegrityHead;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DataIntegrityHeadRepository extends JpaRepository<DataIntegrityHead, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from DataIntegrityHead h where h.deviceCode = :deviceCode")
    Optional<DataIntegrityHead> findForUpdate(@Param("deviceCode") String deviceCode);

    @Modifying
    @Query(value = "INSERT IGNORE INTO data_integrity_head "
            + "(device_code, latest_sequence, latest_mac, key_version) VALUES (:deviceCode, 0, '', 1)",
            nativeQuery = true)
    void insertHeadIfAbsent(@Param("deviceCode") String deviceCode);
}
