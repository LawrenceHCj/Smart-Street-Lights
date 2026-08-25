package com.smartlamp.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "device")
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String location;
    private String status;        // ONLINE / OFFLINE
    private Double latestLux;     // 最新光照值
    private Long lastSeen;        // 最后心跳毫秒时间戳
    private Boolean lightOn = false; // 当前灯是否开启，默认关

    private LocalDateTime createdAt;

    private Double longitude;
    private Double latitude;
}