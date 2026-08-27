package com.smartlamp.repository;

import com.smartlamp.entity.DeviceHealthReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceHealthReportRepository extends JpaRepository<DeviceHealthReport, Long> {

    // 这是一个 Spring Data JPA 的方法
    // 只要按照这个特定的英文语法规则命名，Spring 会在底层自动帮你写好极度复杂的 SQL 语句：
    // 也就是根据 deviceCode 查找，按 createdAt 时间倒序排列，只取第一条（也就是该设备的最新体检报告）
    DeviceHealthReport findFirstByDeviceCodeOrderByCreatedAtDesc(String deviceCode);

    List<DeviceHealthReport> findTop30ByDeviceCodeOrderByCreatedAtDesc(String deviceCode);

    @Query("SELECT report FROM DeviceHealthReport report WHERE report.id IN "
            + "(SELECT MAX(latest.id) FROM DeviceHealthReport latest GROUP BY latest.deviceCode)")
    List<DeviceHealthReport> findLatestForAllDevices();

    void deleteByDeviceCode(String deviceCode);

    /**
     * 按创建时间清理过期报告，避免健康报告表无限增长。
     * 配合巡检降频后，可保留 N 天的报告用于历史展示。
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DeviceHealthReport r WHERE r.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
