package com.smartlamp.agent.actions;

// 业务执行器接口：ActionGateway 检查全部通过后才会调用。
// 返回 ExecutorResult 如实报告执行结果（ActionGateway 依据 status 决定 Action 终态）；
// 返回 null 表示执行器不报告结果（按默认"执行成功"处理）。
// 安全红线：未收到设备回执时不得返回 DEVICE_CONFIRMED（否则会虚假标记 SUCCESS）。
@FunctionalInterface
public interface ActionExecutor {

    ExecutorResult execute(AgentAction action);
}
