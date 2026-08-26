package com.smartlamp.agent.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Action Gateway：Agent 一切写操作的唯一出口。
// 执行前依次检查：存在性 → 有效期 → 风险等级 → 确认状态（HIGH_WRITE 与未确认 Action 在此被拦截）。
// 检查全部通过后才会查找业务执行器；本阶段未注册任何执行器，不会触碰任何业务 Service / MQTT / 数据库写操作。
// 安全红线：状态不是 CONFIRMED 的 Action 绝不会到达执行器（"未确认 Action 绝不能调用正式 Service"）。
@Component
public class ActionGateway {

    @Autowired
    private ActionManager actionManager;

    // 业务执行器注册表（后续阶段将 3号成员的正式 Service 包装后注册）
    private final Map<ActionType, ActionExecutor> executors = new ConcurrentHashMap<>();

    public void registerExecutor(ActionType type, ActionExecutor executor) {
        if (type.getRisk() == ActionRisk.HIGH_WRITE) {
            throw new ActionRejectedException("高风险操作不允许注册执行器: " + type.getDisplayName());
        }
        executors.put(type, executor);
    }

    public synchronized AgentAction execute(String actionId) {
        AgentAction action = actionManager.find(actionId)
                .orElseThrow(() -> new ActionRejectedException("Action 不存在: " + actionId));

        // 1. 有效期检查（懒过期：过期即置 EXPIRED 并拦截）
        actionManager.checkExpiry(action);

        // 2. 风险等级检查：HIGH_WRITE 一律拒绝（双保险，创建时已拒绝）
        if (action.getRiskLevel() == ActionRisk.HIGH_WRITE) {
            throw new ActionRejectedException("高风险操作，Agent 禁止执行: " + action.getActionType().getDisplayName());
        }

        // 3. 确认状态检查：只有 CONFIRMED 才能执行（READ 创建即 CONFIRMED；LOW_WRITE 需用户确认）
        if (action.getStatus() != ActionStatus.CONFIRMED) {
            throw new ActionRejectedException("Action 未确认，禁止执行（当前状态: " + action.getStatus() + "）");
        }

        // 4. 全部检查通过，进入执行态
        actionManager.markExecuting(action.getActionId());
        try {
            ActionExecutor executor = executors.get(action.getActionType());
            if (executor != null) {
                executor.execute(action);
                actionManager.markSuccess(action.getActionId(), "执行成功");
            } else {
                // 本阶段未接入业务执行器：协议层检查通过即视为成功，
                // 真实业务执行在后续阶段接入 3号成员的正式 Service
                actionManager.markSuccess(action.getActionId(),
                        "已通过安全网关检查（业务执行器未接入，后续阶段连接 3号 Service）");
            }
        } catch (Exception e) {
            actionManager.markFailure(action.getActionId(), e.getMessage());
            throw new ActionRejectedException("Action 执行失败: " + e.getMessage(), e);
        }
        return action;
    }
}
