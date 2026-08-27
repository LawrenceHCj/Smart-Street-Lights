package com.smartlamp.repository;

import com.smartlamp.entity.DeviceHealthReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceHealthReportRepository extends JpaRepository<DeviceHealthReport, Long> {

    DeviceHealthReport findFirstByDeviceCodeOrderByCreatedAtDesc(String deviceCode);

    List<DeviceHealthReport> findTop30ByDeviceCodeOrderByCreatedAtDesc(String deviceCode);

    @Query("SELECT report FROM DeviceHealthReport report WHERE report.id IN "
            + "(SELECT MAX(latest.id) FROM DeviceHealthReport latest GROUP BY latest.deviceCode)")
    List<DeviceHealthReport> findLatestForAllDevices();

    void deleteByDeviceCode(String deviceCode);

    // 新增：按时间清理过期报告
    void deleteByCreatedAtBefore(LocalDateTime time);
}