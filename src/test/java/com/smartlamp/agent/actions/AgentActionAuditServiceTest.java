package com.smartlamp.agent.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// 阶段19：Agent 写操作审计单测（成功/失败/取消/过期均有日志，审计与 Action 状态一致）
// 仓库用内存 Map 模拟，不依赖 MySQL
@ExtendWith(MockitoExtension.class)
class AgentActionAuditServiceTest {

    @Mock
    private AgentActionAuditRepository repository;

    // 模拟数据库：actionId → 审计记录
    private final Map<String, AgentActionAudit> db = new HashMap<>();

    // 模拟数据库故障开关（审计写入失败场景）
    private final AtomicBoolean dbDown = new AtomicBoolean(false);

    private ActionManager manager;
    private AgentActionAuditService auditService;

    @BeforeEach
    void setUp() {
        manager = new ActionManager();
        auditService = new AgentActionAuditService(repository, manager); // 构造时注册审计钩子
        when(repository.findByActionId(any())).thenAnswer(
                inv -> Optional.ofNullable(db.get(inv.getArgument(0, String.class))));
        when(repository.save(any())).thenAnswer(inv -> {
            if (dbDown.get()) throw new RuntimeException("数据库不可用");
            AgentActionAudit audit = inv.getArgument(0);
            if (audit == null) return null; // 打桩占位调用（any() 匹配器返回 null）
            if (audit.getId() == null) audit.setId((long) (db.size() + 1));
            db.put(audit.getActionId(), audit);
            return audit;
        });
    }

    private AgentAction createAction(ActionType type, String code, long ttlMs) {
        AgentAction action = manager.create(type, "device", code, Map.of(), "test-user", ttlMs);
        action.setOriginalState("OFF");
        action.setTargetState("ON");
        auditService.recordCreated(action);
        return action;
    }

    private AgentActionAudit auditOf(String actionId) {
        return db.get(actionId);
    }

    // ============ 成功动作有日志 ============

    @Test
    void 成功动作有日志且时间点完整() {
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001", 60_000);
        manager.confirm(action.getActionId());
        manager.markExecuting(action.getActionId());
        manager.markSuccess(action.getActionId(), "执行成功");

        AgentActionAudit audit = auditOf(action.getActionId());
        assertThat(audit).isNotNull();
        assertThat(audit.getSource()).isEqualTo(AgentActionAudit.SOURCE_AI_AGENT);
        assertThat(audit.getRequestedBy()).isEqualTo("test-user");
        assertThat(audit.getActionType()).isEqualTo(ActionType.TURN_ON_LIGHT);
        assertThat(audit.getTargetId()).isEqualTo("lamp001");
        assertThat(audit.getArguments()).isEqualTo("{}");
        assertThat(audit.getOriginalState()).isEqualTo("OFF");
        assertThat(audit.getTargetState()).isEqualTo("ON");
        assertThat(audit.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(audit.getRequestedAt()).isEqualTo(action.getRequestedAt());
        assertThat(audit.getConfirmedAt()).isNotNull();
        assertThat(audit.getExecutedAt()).isNotNull();
        assertThat(audit.getResult()).contains("执行成功");
        assertThat(audit.getError()).isNull();
    }

    // ============ 失败动作有日志 ============

    @Test
    void 失败动作有日志且error完整() {
        AgentAction action = createAction(ActionType.TURN_OFF_LIGHT, "lamp001", 60_000);
        manager.confirm(action.getActionId());
        manager.markExecuting(action.getActionId());
        manager.markFailure(action.getActionId(), "MQTT 发布失败");

        AgentActionAudit audit = auditOf(action.getActionId());
        assertThat(audit.getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(audit.getError()).contains("执行失败").contains("MQTT 发布失败");
        assertThat(audit.getResult()).isNull();
    }

    // ============ 取消动作有日志 ============

    @Test
    void 取消动作有日志() {
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001", 60_000);
        manager.cancel(action.getActionId());

        AgentActionAudit audit = auditOf(action.getActionId());
        assertThat(audit.getStatus()).isEqualTo(ActionStatus.CANCELLED);
        assertThat(audit.getResult()).contains("已由用户取消");
    }

    // ============ 过期动作有日志 ============

    @Test
    void 过期动作有日志() throws InterruptedException {
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001", 1L);
        Thread.sleep(10);

        assertThatThrownBy(() -> manager.confirm(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class);
        AgentActionAudit audit = auditOf(action.getActionId());
        assertThat(audit.getStatus()).isEqualTo(ActionStatus.EXPIRED);
        assertThat(audit.getError()).contains("过期");
    }

    // ============ Action 状态和审计日志一致 ============

    @Test
    void 审计日志状态与Action状态一致() {
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001", 60_000);
        manager.confirm(action.getActionId());

        assertThat(auditOf(action.getActionId()).getStatus())
                .isEqualTo(manager.find(action.getActionId()).orElseThrow().getStatus());

        manager.markExecuting(action.getActionId());
        manager.markAccepted(action.getActionId(), "COMMAND_ACCEPTED：控制指令已发送，但当前尚未获得设备执行确认");

        assertThat(auditOf(action.getActionId()).getStatus())
                .isEqualTo(ActionStatus.COMMAND_ACCEPTED)
                .isEqualTo(manager.find(action.getActionId()).orElseThrow().getStatus());
        assertThat(auditOf(action.getActionId()).getResult()).contains("COMMAND_ACCEPTED");
    }

    // ============ 审计自身异常不阻断控制流程 ============

    @Test
    void 审计写入失败不影响控制流程() {
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001", 60_000);

        manager.confirm(action.getActionId()); // 先正常确认
        dbDown.set(true);
        manager.markExecuting(action.getActionId()); // 审计落库失败也不阻断

        assertThat(manager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.EXECUTING);
    }
}
