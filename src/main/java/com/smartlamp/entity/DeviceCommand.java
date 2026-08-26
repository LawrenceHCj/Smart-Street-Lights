package com.smartlamp.entity;

import com.smartlamp.entity.enums.CommandStatus;
import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "device_command", indexes = {
        @Index(name = "idx_command_id", columnList = "commandId", unique = true),
        @Index(name = "idx_command_device", columnList = "deviceCode,createdAt")
})
public class DeviceCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String commandId;      // UUID

    @Column(nullable = false)
    private String deviceCode;     // 设备编号

    @Column(nullable = false)
    private String action;         // ON / OFF

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandStatus status = CommandStatus.DISPATCHED;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}