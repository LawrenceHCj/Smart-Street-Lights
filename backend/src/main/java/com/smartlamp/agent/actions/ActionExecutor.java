package com.smartlamp.agent.actions;

// 业务执行器接口：ActionGateway 检查全部通过后才会调用。
// 后续阶段将 3号成员的正式 Service（如开关灯）包装后通过 ActionGateway.registerExecutor 注册；
// 本阶段不注册任何执行器，也不会触碰任何业务 Service / MQTT / 数据库写操作。
@FunctionalInterface
public interface ActionExecutor {

    void execute(AgentAction action) throws Exception;
}
