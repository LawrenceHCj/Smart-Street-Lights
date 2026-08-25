package com.smartlamp.agent.actions;

// Action 生命周期状态机：
//   LOW_WRITE：PENDING_CONFIRMATION → CONFIRMED → EXECUTING → SUCCESS / FAILED
//              PENDING_CONFIRMATION → CANCELLED（用户取消）
//              未终态时超过 expiresAt → EXPIRED
//   READ     ：创建即 CONFIRMED → EXECUTING → SUCCESS / FAILED
public enum ActionStatus {
    PENDING_CONFIRMATION, // 等待用户确认（LOW_WRITE 初始态）
    CONFIRMED,            // 已确认，允许执行
    EXECUTING,            // 执行中
    SUCCESS,              // 执行成功
    FAILED,               // 执行失败
    CANCELLED,            // 用户取消
    EXPIRED               // 超过有效期
}
