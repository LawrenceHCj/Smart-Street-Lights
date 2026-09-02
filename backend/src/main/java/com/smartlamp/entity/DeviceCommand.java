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

    /** 控制来源：MANUAL / AGENT / TIME_SCHEDULE 等（证据链命令载荷需要） */
    @Column(name = "mode", length = 32)
    private String mode = "MANUAL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandStatus status = CommandStatus.DISPATCHED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
