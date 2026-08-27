package com.smartlamp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// 控制命令执行结果（阶段18）：status 必须区分 COMMAND_ACCEPTED / DEVICE_CONFIRMED / FAILED / TIMEOUT，
// 调用方不得仅凭"命令已发送"就对用户说"设备已成功开启"。
@Data
@AllArgsConstructor
public class ControlOutcome {

    private String commandId;   // 命令号（FAILED 时可能为 null）
    private String deviceId;
    private String action;      // ON / OFF
    private CommandStatus status;
    private Long issuedAt;      // 下发时间（epoch ms）
    private String message;

    public static ControlOutcome failed(String message) {
        return new ControlOutcome(null, null, null, CommandStatus.FAILED, System.currentTimeMillis(), message);
    }

    public static ControlOutcome accepted(String commandId, String deviceId, String action, Long issuedAt, String message) {
        return new ControlOutcome(commandId, deviceId, action, CommandStatus.COMMAND_ACCEPTED, issuedAt, message);
    }

    public static ControlOutcome confirmed(String commandId, String deviceId, String action, Long issuedAt, String message) {
        return new ControlOutcome(commandId, deviceId, action, CommandStatus.DEVICE_CONFIRMED, issuedAt, message);
    }

    public static ControlOutcome timeout(String commandId, String deviceId, String action, Long issuedAt, String message) {
        return new ControlOutcome(commandId, deviceId, action, CommandStatus.TIMEOUT, issuedAt, message);
    }
}
