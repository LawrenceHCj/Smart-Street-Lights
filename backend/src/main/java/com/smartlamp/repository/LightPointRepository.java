package com.smartlamp.repository;

import com.smartlamp.entity.LightPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LightPointRepository extends JpaRepository<LightPoint, Long> {
    boolean existsByDeviceCodeAndTs(String deviceCode, Long ts);
    List<LightPoint> findByDeviceCodeAndTsBetweenOrderByTsAsc(String deviceCode, Long start, Long end);
    List<LightPoint> findByDeviceCodeOrderByTsDesc(String deviceCode, org.springframework.data.domain.Pageable pageable);
    List<LightPoint> findAllByOrderByTsDesc(org.springframework.data.domain.Pageable pageable);
    void deleteByDeviceCode(String deviceCode);
    void deleteByTsBefore(long ts);

    /**
     * 节能估算使用 5 分钟桶，避免把高频 MQTT 原始点全部加载进 JVM。
     * 只统计灯亮且有功率数据的样本，保持“开关策略不变”的比较口径。
     */
    @Query(value = """
            SELECT device_code AS deviceCode,
                   FLOOR(ts / 300000) * 300000 AS bucketTs,
                   AVG(power) AS averageOnPower,
                   MIN(ts) AS firstTs,
                   MAX(ts) AS lastTs,
                   COUNT(*) AS sampleCount
            FROM light_point
            WHERE ts BETWEEN :start AND :end
              AND lamp_status = 'ON'
              AND power IS NOT NULL
              AND power > 0
            GROUP BY device_code, FLOOR(ts / 300000) * 300000
            ORDER BY bucketTs ASC, device_code ASC
            """, nativeQuery = true)
    List<EnergySampleBucket> findEnergyBuckets(@Param("start") long start, @Param("end") long end);

    interface EnergySampleBucket {
        String getDeviceCode();
        Long getBucketTs();
        Double getAverageOnPower();
        Long getFirstTs();
        Long getLastTs();
        Long getSampleCount();
    }
}
