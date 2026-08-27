package com.smartlamp.repository;

import com.smartlamp.entity.Alarm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {
    List<Alarm> findAllByOrderByTsDesc();
    long countByStatus(String status);
    void deleteByDeviceId(String deviceId);

    @Query("SELECT a FROM Alarm a WHERE a.deviceId = :deviceId AND a.type = :type AND a.status IN :statuses ORDER BY a.lastOccurredAt DESC, a.ts DESC")
    List<Alarm> findRecentByDeviceAndType(@Param("deviceId") String deviceId,
                                          @Param("type") String type,
                                          @Param("statuses") List<String> statuses);

    @Query("SELECT a FROM Alarm a WHERE a.deviceId = :deviceId AND a.type = '离线' AND a.status IN ('OPEN', 'ACKED')")
    List<Alarm> findUnrecoveredOfflineAlarms(@Param("deviceId") String deviceId);
}
