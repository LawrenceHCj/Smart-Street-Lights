package com.smartlamp.agent.actions;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

    // 开放的目标类型（阶段20 起增加 config：系统配置类目标）
    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of("device", "config");

    private final Map<String, AgentAction> store = new ConcurrentHashMap<>();

    // 审计钩子（阶段19）：每次状态流转后回调，由 AgentActionAuditService 注册；未注册时无操作（纯内存单测不受影响）
    private volatile Consumer<AgentAction> auditHook;

    public void setAuditHook(Consumer<AgentAction> auditHook) {
        this.auditHook = auditHook;
    }

    private void notifyAudit(AgentAction action) {
        Consumer<AgentAction> hook = auditHook;
        if (hook != null) {
            hook.accept(action);
        }
    }

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
        notifyAudit(action);
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
        notifyAudit(action);
    }

    // 懒过期检查：未终态且超过 expiresAt 的 Action 置为 EXPIRED 并抛异常拦截
    public synchronized void checkExpiry(AgentAction action) {
        if ((action.getStatus() == ActionStatus.PENDING_CONFIRMATION || action.getStatus() == ActionStatus.CONFIRMED)
                && System.currentTimeMillis() > action.getExpiresAt()) {
            action.setStatus(ActionStatus.EXPIRED);
            action.setMessage("已过期（有效期至 " + action.getExpiresAt() + "）");
            notifyAudit(action);
            throw new ActionRejectedException("Action 已过期: " + action.getActionId());
        }
    }

    // 参数二次复核：创建时已校验，确认/执行前再次校验作为第二道防线
    public void revalidate(AgentAction action) {
        validateTarget(action.getTargetType(), action.getTargetId());
        validateArguments(action.getActionType(), action.getArguments());
    }

    // 取消指定会话的全部待确认 Action（阶段30：会话删除时的安全处理）。
    // 只取消 PENDING_CONFIRMATION，已确认/终态不受影响；conversationId 仅用于溯源关联，
    // 绝不能替代 actionId 的精确确认。
    public synchronized int cancelPendingByConversation(String conversationId) {
        int count = 0;
        for (AgentAction action : store.values()) {
            if (conversationId != null && conversationId.equals(action.getConversationId())
                    && action.getStatus() == ActionStatus.PENDING_CONFIRMATION) {
                action.setStatus(ActionStatus.CANCELLED);
                action.setMessage("所属会话已删除，操作已取消（conversationId=" + conversationId + "）");
                notifyAudit(action);
                count++;
            }
        }
        return count;
    }

    // ============ 以下状态流转仅供 ActionGateway 使用（包内可见） ============

    synchronized void markExecuting(String actionId) {
        AgentAction action = require(actionId);
        if (action.getStatus() != ActionStatus.CONFIRMED) {
            throw new IllegalStateException("只有 CONFIRMED 状态的 Action 才能执行（当前: " + action.getStatus() + "）");
        }
        action.setStatus(ActionStatus.EXECUTING);
        action.setMessage("执行中");
        notifyAudit(action);
    }

    synchronized void markSuccess(String actionId, String message) {
        AgentAction action = require(actionId);
        action.setStatus(ActionStatus.SUCCESS);
        action.setMessage(message);
        notifyAudit(action);
    }

    synchronized void markAccepted(String actionId, String message) {
        AgentAction action = require(actionId);
        action.setStatus(ActionStatus.COMMAND_ACCEPTED);
        action.setMessage(message);
        notifyAudit(action);
    }

    synchronized void markFailure(String actionId, String message) {
        AgentAction action = require(actionId);
        action.setStatus(ActionStatus.FAILED);
        action.setMessage("执行失败: " + message);
        notifyAudit(action);
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
            // 合法范围以后端业务规则为准（与 PUT /api/config 的 luxThreshold 10-500 规则一致）
            if (!(value instanceof Number) || ((Number) value).doubleValue() < 10 || ((Number) value).doubleValue() > 500) {
                throw new ActionRejectedException("阈值参数 value 必须是 10-500 之间的数值（以后端配置规则为准）");
            }
        }
        if (type == ActionType.UPDATE_AUTO_MODE && args.containsKey("enabled")) {
            if (!(args.get("enabled") instanceof Boolean)) {
                throw new ActionRejectedException("自动模式参数 enabled 必须是布尔值");
            }
        }
        // 批量开/关的区域限定词：非空字符串且不超过 32 字
        if ((type == ActionType.TURN_OFF_ALL || type == ActionType.TURN_ON_ALL)
                && args.containsKey("locationKeyword")) {
            Object keyword = args.get("locationKeyword");
            if (!(keyword instanceof String) || ((String) keyword).isBlank() || ((String) keyword).length() > 32) {
                throw new ActionRejectedException("区域限定词 locationKeyword 必须是非空字符串且不超过 32 字");
            }
        }
    }

    private AgentAction require(String actionId) {
        return find(actionId)
                .orElseThrow(() -> new ActionRejectedException("Action 不存在: " + actionId));
    }
}
