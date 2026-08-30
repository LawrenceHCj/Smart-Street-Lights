package com.smartlamp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设备健康体检报告
 */
@Data
@Entity
@Table(
        name = "device_health_report",
        indexes = {
                // 解决 findTop30ByDeviceCodeOrderByCreatedAtDesc 与 findLatestForAllDevices 的查询性能问题。
                @Index(name = "idx_health_device_created", columnList = "device_code, created_at")
        }
)
public class DeviceHealthReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联的设备编号 (例如: SL-001)
    @Column(name = "device_code", nullable = false)
    private String deviceCode;

    // 综合健康分 (0 - 100)
    @Column(name = "health_score", nullable = false)
    private Integer healthScore;

    // 异常指标与判断依据 (为了方便扩展，我们存成 JSON 格式的字符串)
    // 例如: [{"issue": "电压波动频繁", "deduct": 10}, {"issue": "温度过热", "deduct": 15}]
    @Column(name = "anomaly_details", columnDefinition = "TEXT")
    private String anomalyDetails;

    // 生成评分时使用的采集数据快照，确保历史报告可追溯。
    @Column(name = "telemetry_snapshot", columnDefinition = "TEXT")
    private String telemetrySnapshot;

    // 本次体检报告的生成时间
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
