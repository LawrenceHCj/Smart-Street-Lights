package com.smartlamp.agent.actions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

// Agent 写操作审计服务（阶段19）：所有 Agent 写操作的持久化审计记录。
//  - 创建审计由 AgentActionTools 调用 recordCreated（携带 originalState/targetState 快照）；
//  - 之后每次状态流转由 ActionManager 的审计钩子回调 onTransition（构造时注册），
//    按 actionId upsert 同一条记录——失败、取消、过期、超时同样落库，与 Action 状态保持一致。
//  - 审计自身异常绝不阻断控制流程（静默降级，只记日志）。
//  - 存储纪律：只存结构化字段与白名单 arguments，不存 API Key / Token / Prompt 秘密。
@Slf4j
@Service
public class AgentActionAuditService {

    private final AgentActionAuditRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 构造时注册 ActionManager 的审计钩子（状态流转 → 审计落库）
    public AgentActionAuditService(AgentActionAuditRepository repository, ActionManager actionManager) {
        this.repository = repository;
        actionManager.setAuditHook(this::onTransition);
    }

    // 创建审计（AgentActionTools 在生成待确认 Action 成功后调用，携带状态快照）
    public void recordCreated(AgentAction action) {
        try {
            upsert(action);
        } catch (Exception e) {
            log.warn("Action 创建审计写入失败（不影响主流程）: actionId={}, reason={}",
                    action.getActionId(), e.getMessage());
        }
    }

    // 状态流转审计（ActionManager 钩子回调）
    private void onTransition(AgentAction action) {
        try {
            upsert(action);
        } catch (Exception e) {
            log.warn("Action 状态审计写入失败（不影响主流程）: actionId={}, reason={}",
                    action.getActionId(), e.getMessage());
        }
    }

    // 按 actionId 查找已有审计记录并更新（无则新建）
    private void upsert(AgentAction action) {
        AgentActionAudit audit = repository.findByActionId(action.getActionId()).orElse(null);
        if (audit == null) {
            audit = new AgentActionAudit();
            audit.setActionId(action.getActionId());
            audit.setSource(AgentActionAudit.SOURCE_AI_AGENT);
            audit.setCreatedAt(LocalDateTime.now());
        }
        long now = System.currentTimeMillis();

        audit.setRequestedBy(action.getRequestedBy());
        audit.setActionType(action.getActionType());
        audit.setTargetType(action.getTargetType());
        audit.setTargetId(action.getTargetId());
        audit.setArguments(serializeArguments(action));
        audit.setOriginalState(action.getOriginalState());
        audit.setTargetState(action.getTargetState());
        audit.setStatus(action.getStatus());
        audit.setRequestedAt(action.getRequestedAt());

        // 时间点：确认时间 / 开始执行时间（首次流转到相应阶段时记录）
        if (audit.getConfirmedAt() == null && action.getStatus() == ActionStatus.CONFIRMED) {
            audit.setConfirmedAt(now);
        }
        if (audit.getExecutedAt() == null && isExecutingOrAfter(action.getStatus())) {
            audit.setExecutedAt(now);
        }

        // 结果与错误：成功/已接受/已取消记入 result；失败/过期记入 error
        switch (action.getStatus()) {
            case SUCCESS, COMMAND_ACCEPTED, CANCELLED -> {
                audit.setResult(action.getMessage());
                audit.setError(null);
            }
            case FAILED, EXPIRED -> {
                audit.setError(action.getMessage());
                audit.setResult(null);
            }
            default -> {
                // CONFIRMED / EXECUTING 等中间态：保留已有结果字段，仅更新时间戳
            }
        }
        audit.setUpdatedAt(LocalDateTime.now());
        repository.save(audit);
    }

    private boolean isExecutingOrAfter(ActionStatus status) {
        return status == ActionStatus.EXECUTING || status == ActionStatus.SUCCESS
                || status == ActionStatus.COMMAND_ACCEPTED || status == ActionStatus.FAILED;
    }

    // 结构化参数序列化（白名单键校验后的 Map；序列化失败不阻断审计）
    private String serializeArguments(AgentAction action) {
        try {
            return objectMapper.writeValueAsString(
                    action.getArguments() == null ? java.util.Map.of() : action.getArguments());
        } catch (Exception e) {
            return "{}";
        }
    }
}
