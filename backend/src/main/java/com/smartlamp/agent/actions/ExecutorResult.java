package com.smartlamp.agent.actions;

import com.smartlamp.dto.CommandStatus;

// 执行器结果：ActionGateway 依据 status 决定 Action 终态。
//  - DEVICE_CONFIRMED → Action 置 SUCCESS（真正成功，已收到设备回执）
//  - COMMAND_ACCEPTED → Action 置 COMMAND_ACCEPTED（命令已下发但未收到回执，绝不标记成功）
//  - FAILED / TIMEOUT  → Action 置 FAILED 并拒绝（消息如实）
public record ExecutorResult(CommandStatus status, String message) {
}
