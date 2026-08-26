package com.smartlamp.entity;

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

    @Column(nullable = false)
    private String level;       // info / warning / critical

    @Column(nullable = false)
    private String message;     // 告警内容

    @Column(nullable = false)
    private Long ts;            // 触发毫秒时间戳

    @Column(nullable = false)
    private String status;      // OPEN / ACKED / RECOVERED

    private LocalDateTime firstOccurredAt;   // 首次发生时间
    private LocalDateTime lastOccurredAt;    // 最后发生时间
    private Integer occurrenceCount = 1;     // 发生次数

    private LocalDateTime createdAt;
}
