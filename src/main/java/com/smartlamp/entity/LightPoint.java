package com.smartlamp.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "light_point")
public class LightPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceCode;   // 设备编号

    @Column(nullable = false)
    private Double lux;          // 光照值

    @Column(nullable = false)
    private Long ts;             // 毫秒时间戳

    private LocalDateTime createdAt;
}