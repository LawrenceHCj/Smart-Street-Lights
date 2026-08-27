package com.smartlamp.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "light_point", indexes = {
        @Index(name = "idx_device_ts", columnList = "device_code,ts", unique = true)
})
public class LightPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceCode;

    @Column(nullable = false)
    private Double lux;

    @Column(nullable = false)
    private Long ts;

    private Double temperature;
    private Double voltage;
    private Double current;
    private Double power;
    private Double energy;
    private String lampStatus;
    private String rawPayload;

    private LocalDateTime createdAt;
    private LocalDateTime serverReceivedAt;
}