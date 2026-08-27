package com.smartlamp.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "device_health_report", indexes = {
        @Index(name = "idx_health_device_created", columnList = "device_code,created_at")
})
public class DeviceHealthReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceCode;

    @Column(nullable = false)
    private Integer healthScore;

    @Column(columnDefinition = "TEXT")
    private String anomalyDetails;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}