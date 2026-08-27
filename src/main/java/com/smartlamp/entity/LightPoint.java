package com.smartlamp.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "light_point",
        indexes = @Index(name = "idx_light_point_device_ts", columnList = "device_code, ts"),
        uniqueConstraints = @UniqueConstraint(name = "uk_light_point_device_ts", columnNames = {"device_code", "ts"})
)
public class LightPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false)
    private String deviceCode;   // 设备编号

    @Column(nullable = false)
    private Double lux;          // 光照值

    private Double temperature;
    private Double voltage;
    private Double current;
    private Double power;
    private Double energy;
    private String lampStatus;

    @Column(name = "ts", nullable = false)
    private Long ts;             // 毫秒时间戳

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String rawPayload;

    private LocalDateTime createdAt;
}
