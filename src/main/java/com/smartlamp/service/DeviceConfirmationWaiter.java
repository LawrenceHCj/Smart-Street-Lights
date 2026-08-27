package com.smartlamp.service;

// 设备执行确认等待器（4号 反馈链路就绪后实现，当前无实现）：
// 控制命令下发后，检查设备是否已确认执行。
// 实现约定：必须基于设备真实反馈（cmdAck topic / 遥测回读），
// 不得用后端乐观更新后的 lampStatus 判断，否则会产生虚假确认。
@FunctionalInterface
public interface DeviceConfirmationWaiter {

    // 设备是否已确认执行目标状态（targetLampStatus 为 ON / OFF）
    boolean isConfirmed(String deviceId, String targetLampStatus);
}
