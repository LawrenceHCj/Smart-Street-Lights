package com.smartlamp.entity;

import com.smartlamp.entity.enums.DeviceStatus;
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

    private Double longitude;
    private Double latitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status = DeviceStatus.OFFLINE;  // 默认离线

    private Double latestLux;
    private Long lastSeen;
    private Boolean lightOn = false;

    private LocalDateTime createdAt;
}