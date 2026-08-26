package com.smartlamp.repository;

import com.smartlamp.entity.Alarm;
import com.smartlamp.entity.enums.AlarmStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {
    List<Alarm> findAllByOrderByTsDesc();

    // 按设备编号删除（级联删除时使用）
    void deleteByDeviceId(String deviceId);

    // 查询指定设备、类型、状态集合的最近一条告警（用于去重）
    @Query("SELECT a FROM Alarm a WHERE a.deviceId = :deviceId AND a.type = :type AND a.status IN :statuses ORDER BY a.lastOccurredAt DESC")
    List<Alarm> findRecentByDeviceAndType(@Param("deviceId") String deviceId,
                                          @Param("type") String type,
                                          @Param("statuses") List<AlarmStatus> statuses);

    // 查询指定设备的未恢复离线告警
    @Query("SELECT a FROM Alarm a WHERE a.deviceId = :deviceId AND a.type = '离线' AND a.status <> 'RECOVERED'")
    List<Alarm> findUnrecoveredOfflineAlarms(@Param("deviceId") String deviceId);
}