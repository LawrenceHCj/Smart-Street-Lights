package com.smartlamp.repository;

import com.smartlamp.entity.DeviceRiskPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceRiskPredictionRepository extends JpaRepository<DeviceRiskPrediction, Long> {

    List<DeviceRiskPrediction> findTop30ByDeviceCodeOrderByPredictedAtDesc(String deviceCode);

    /** 全部设备的最新一次预测（用于风险预测列表页） */
    @Query("SELECT p FROM DeviceRiskPrediction p WHERE p.id IN "
            + "(SELECT MAX(latest.id) FROM DeviceRiskPrediction latest GROUP BY latest.deviceCode)")
    List<DeviceRiskPrediction> findLatestForAllDevices();

    /** 历史预测清理，防止表无限增长（默认保留 30 天） */
    @Modifying
    @Transactional
    @Query("DELETE FROM DeviceRiskPrediction p WHERE p.predictedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
