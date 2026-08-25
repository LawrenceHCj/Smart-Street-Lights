package com.smartlamp.agent.actions;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Action 管理器：Action 的创建、查询、确认/取消与状态流转，全部 Action 实例保存在内存。
// 安全约定：
//  - 所有可执行 Action 必须来自 ActionType 白名单；HIGH_WRITE 创建即拒绝；
//  - 禁止万能执行命令：arguments 黑名单键（command/sql/payload 等）与未知键一律拒绝；
//  - LOW_WRITE 创建后处于 PENDING_CONFIRMATION，必须 confirm() 后才能执行；
//  - 每个 Action 都有有效期（expiresAt），过期自动置 EXPIRED 并拦截。
@Component
public class ActionManager {

    public static final long DEFAULT_TTL_MS = 120_000L; // 默认有效期 2 分钟

    // 安全红线：这些参数键一律禁止出现在 arguments 中（防万能命令 / 任意 SQL / 任意 MQTT payload）
    private static final Set<String> FORBIDDEN_ARGUMENT_KEYS =
            Set.of("command", "cmd", "sql", "shell", "script", "payload", "topic", "mqtt");

    // 本阶段开放的目标类型（后续开放配置类目标时再扩展）
    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of("device");

    private final Map<String, AgentAction> store = new ConcurrentHashMap<>();

    public synchronized AgentAction create(ActionType type, String targetType, String targetId,
                                           Map<String, Object> arguments, String requestedBy) {
        return create(type, targetType, targetId, arguments, requestedBy, DEFAULT_TTL_MS);
    }

    public synchronized AgentAction create(ActionType type, String targetType, String targetId,
                                           Map<String, Object> arguments, String requestedBy, long ttlMs) {
        // 1. 白名单与风险检查
        if (type.getRisk() == ActionRisk.HIGH_WRITE) {
            throw new ActionRejectedException("高风险操作，Agent 禁止执行: " + type.getDisplayName());
        }
        if (!type.isAllowed()) {
            throw new ActionRejectedException("该操作当前阶段未开放: " + type.getDisplayName());
        }
        // 2. 结构化参数校验
        validateTarget(targetType, targetId);
        validateArguments(type, arguments);

        long now = System.currentTimeMillis();
        AgentAction action = new AgentAction();
        action.setActionId(UUID.randomUUID().toString());
        action.setActionType(type);
        action.setTargetType(targetType);
        action.setTargetId(targetId.trim());
        action.setArguments(arguments == null ? Map.of() : Map.copyOf(arguments));
        action.setRiskLevel(type.getRisk());
        // READ 免确认直接可执行；LOW_WRITE 必须等待用户确认
        action.setStatus(type.getRisk() == ActionRisk.READ ? ActionStatus.CONFIRMED : ActionStatus.PENDING_CONFIRMATION);
        action.setRequestedAt(now);
        action.setExpiresAt(now + Math.max(1L, ttlMs));
        action.setRequestedBy(requestedBy);
        action.setMessage(type.getDisplayName() + " 已创建（" + action.getStatus() + "）");
        store.put(action.getActionId(), action);
        return action;
    }

    public Optional<AgentAction> find(String actionId) {
        return Optional.ofNullable(store.get(actionId));
    }

    // 用户确认：PENDING_CONFIRMATION → CONFIRMED
    public synchronized void confirm(String actionId) {
        AgentAction action = require(actionId);
        checkExpiry(action);
        if (action.getStatus() != ActionStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("只有 PENDING_CONFIRMATION 状态的 Action 才能确认（当前: " + action.getStatus() + "）");
        }
        action.setStatus(ActionStatus.CONFIRMED);
        action.setMessage("已由用户确认");
    }

    // 用户取消：PENDING_CONFIRMATION → CANCELLED
    public synchronized void cancel(String actionId) {
        AgentAction action = require(actionId);
        checkExpiry(action);
        if (action.getStatus() != ActionStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("只有 PENDING_CONFIRMATION 状态的 Action 才能取消（当前: " + action.getStatus() + "）");
        }
        action.setStatus(ActionStatus.CANCELLED);
        action.setMessage("已由用户取消");
    }

    // 懒过期检查：未终态且超过 expiresAt 的 Action 置为 EXPIRED 并抛异常拦截
    public synchronized void checkExpiry(AgentAction action) {
        if ((action.getStatus() == ActionStatus.PENDING_CONFIRMATION || action.getStatus() == ActionStatus.CONFIRMED)
                && System.currentTimeMillis() > action.getExpiresAt()) {
            action.setStatus(ActionStatus.EXPIRED);
            action.setMessage("已过期（有效期至 " + action.getExpiresAt() + "）");
            throw new ActionRejectedException("Action 已过期: " + action.getActionId());
        }
    }

    // ============ 以下状态流转仅供 ActionGateway 使用（包内可见） ============

    synchronized void markExecuting(String actionId) {
        AgentAction action = require(actionId);
        if (action.getStatus() != ActionStatus.CONFIRMED) {
            throw new IllegalStateException("只有 CONFIRMED 状态的 Action 才能执行（当前: " + action.getStatus() + "）");
        }
        action.setStatus(ActionStatus.EXECUTING);
        action.setMessage("执行中");
    }

    synchronized void markSuccess(String actionId, String message) {
        AgentAction action = require(actionId);
        action.setStatus(ActionStatus.SUCCESS);
        action.setMessage(message);
    }

    synchronized void markFailure(String actionId, String message) {
        AgentAction action = require(actionId);
        action.setStatus(ActionStatus.FAILED);
        action.setMessage("执行失败: " + message);
    }

    // ============ 参数校验 ============

    private void validateTarget(String targetType, String targetId) {
        if (targetType == null || !ALLOWED_TARGET_TYPES.contains(targetType.trim())) {
            throw new ActionRejectedException("不支持的 targetType: " + targetType + "（当前仅允许: " + ALLOWED_TARGET_TYPES + "）");
        }
        if (targetId == null || targetId.isBlank() || targetId.trim().length() > 128) {
            throw new ActionRejectedException("targetId 必须是非空字符串且长度不超过 128");
        }
    }

    private void validateArguments(ActionType type, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        Set<String> allowed = type.getAllowedArgumentKeys();

        for (String key : args.keySet()) {
            // 安全红线：万能命令 / SQL / MQTT payload 等键一律拒绝
            if (FORBIDDEN_ARGUMENT_KEYS.contains(key.toLowerCase())) {
                throw new ActionRejectedException("禁止的参数键: " + key + "（不允许万能命令/SQL/MQTT payload）");
            }
            if (!allowed.contains(key)) {
                throw new ActionRejectedException(type.getDisplayName() + " 不允许的参数键: " + key + "（允许: " + allowed + "）");
            }
        }

        // 已登记类型的参数类型/范围校验（当前阶段未开放的类型同样受约束，开放时直接生效）
        if (type == ActionType.UPDATE_LUX_THRESHOLD && args.containsKey("value")) {
            Object value = args.get("value");
            if (!(value instanceof Number) || ((Number) value).doubleValue() < 0 || ((Number) value).doubleValue() > 500) {
                throw new ActionRejectedException("阈值参数 value 必须是 0-500 之间的数值");
            }
        }
        if (type == ActionType.UPDATE_AUTO_MODE && args.containsKey("enabled")) {
            if (!(args.get("enabled") instanceof Boolean)) {
                throw new ActionRejectedException("自动模式参数 enabled 必须是布尔值");
            }
        }
    }

    private AgentAction require(String actionId) {
        return find(actionId)
                .orElseThrow(() -> new ActionRejectedException("Action 不存在: " + actionId));
    }
}
