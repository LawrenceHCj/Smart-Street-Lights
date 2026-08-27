package com.smartlamp.agent.actions;

// Action 生命周期状态机：
//   LOW_WRITE：PENDING_CONFIRMATION → CONFIRMED → EXECUTING
//              → SUCCESS（已收到设备回执确认）/ COMMAND_ACCEPTED（命令已下发但未收到回执，绝不视为成功）/ FAILED（含等待回执超时）
//              PENDING_CONFIRMATION → CANCELLED（用户取消）
//              未终态时超过 expiresAt → EXPIRED
//   READ     ：创建即 CONFIRMED → EXECUTING → SUCCESS / FAILED
public enum ActionStatus {
    PENDING_CONFIRMATION, // 等待用户确认（LOW_WRITE 初始态）
    CONFIRMED,            // 已确认，允许执行
    EXECUTING,            // 执行中
    SUCCESS,              // 执行成功（已收到设备回执确认，DEVICE_CONFIRMED）
    COMMAND_ACCEPTED,     // 命令已下发，未收到设备回执（终态，不视为成功）
    FAILED,               // 执行失败（含等待设备回执超时）
    CANCELLED,            // 用户取消
    EXPIRED               // 超过有效期
}
