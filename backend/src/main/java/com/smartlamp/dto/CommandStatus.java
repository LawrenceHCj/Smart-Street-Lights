package com.smartlamp.dto;

// 控制命令执行状态（阶段18）："命令已发送" ≠ "设备已执行"，必须如实区分
public enum CommandStatus {

    COMMAND_ACCEPTED("命令已被后端接受（尚未获得设备执行确认）"),
    DEVICE_CONFIRMED("设备已确认执行"),
    FAILED("执行失败"),
    TIMEOUT("等待设备确认超时");

    private final String description;

    CommandStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
