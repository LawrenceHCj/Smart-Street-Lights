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
    private String deviceCode;   // 设备编号

    @Column(nullable = false)
    private Double lux;          // 光照值

    @Column(nullable = false)
    private Long ts;             // 毫秒时间戳（设备采集时间）

    private LocalDateTime createdAt;          // 入库时间
    private LocalDateTime serverReceivedAt;   // 服务器接收时间（新增）
}