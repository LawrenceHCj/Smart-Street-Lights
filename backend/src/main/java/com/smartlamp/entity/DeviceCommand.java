package com.smartlamp.entity;

import com.smartlamp.entity.enums.CommandStatus;
import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "device_command", indexes = {
        @Index(name = "idx_command_id", columnList = "command_id", unique = true),
        @Index(name = "idx_command_device", columnList = "device_code,created_at")
})
public class DeviceCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_id", nullable = false, unique = true)
    private String commandId;      // UUID

    @Column(name = "device_code", nullable = false)
    private String deviceCode;     // 设备编号

    @Column(name = "action", nullable = false)
    private String action;         // ON / OFF

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandStatus status = CommandStatus.DISPATCHED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
