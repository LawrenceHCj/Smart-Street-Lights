package com.smartlamp.entity;

import com.smartlamp.entity.enums.AlarmLevel;
import com.smartlamp.entity.enums.AlarmStatus;
import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "alarm", indexes = {
        @Index(name = "idx_alarm_ts", columnList = "ts"),
        @Index(name = "idx_alarm_status", columnList = "status")
})
public class Alarm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceId;    // 设备编号

    @Column(nullable = false)
    private String type;        // 告警类型，如 "离线"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmLevel level;   // INFO / WARNING / CRITICAL

    @Column(nullable = false)
    private String message;     // 告警内容

    @Column(nullable = false)
    private Long ts;            // 触发毫秒时间戳

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmStatus status; // OPEN / ACKED

    private LocalDateTime createdAt;
}