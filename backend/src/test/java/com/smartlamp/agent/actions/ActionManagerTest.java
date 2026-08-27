package com.smartlamp.agent.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 阶段15：Action 数据模型 + ActionManager 安全边界单测（纯内存，不依赖 Spring/MySQL/MQTT）
class ActionManagerTest {

    private ActionManager manager;

    @BeforeEach
    void setUp() {
        manager = new ActionManager();
    }

    // ============ 创建：关闭 lamp001 ============

    @Test
    void 创建关闭lamp001进入待确认状态且字段完整() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "test-user");

        assertThat(action.getActionId()).isNotBlank();
        assertThat(action.getActionType()).isEqualTo(ActionType.TURN_OFF_LIGHT);
        assertThat(action.getTargetType()).isEqualTo("device");
        assertThat(action.getTargetId()).isEqualTo("lamp001");
        assertThat(action.getRiskLevel()).isEqualTo(ActionRisk.LOW_WRITE);
        assertThat(action.getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
        assertThat(action.getRequestedBy()).isEqualTo("test-user");
        assertThat(action.getExpiresAt()).isEqualTo(action.getRequestedAt() + ActionManager.DEFAULT_TTL_MS);
        assertThat(action.getExpiresAt()).isGreaterThan(action.getRequestedAt());
    }

    @Test
    void actionId全局唯一() {
        AgentAction a = manager.create(ActionType.TURN_ON_LIGHT, "device", "lamp001", Map.of(), "u");
        AgentAction b = manager.create(ActionType.TURN_ON_LIGHT, "device", "lamp001", Map.of(), "u");

        assertThat(a.getActionId()).isNotEqualTo(b.getActionId());
        assertThat(manager.find(a.getActionId())).contains(a);
        assertThat(manager.find(b.getActionId())).contains(b);
    }

    // ============ 取消 / 确认 / 过期 ============

    @Test
    void 取消待确认Action变为CANCELLED() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");

        manager.cancel(action.getActionId());

        assertThat(manager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.CANCELLED);
    }

    @Test
    void 确认待确认Action变为CONFIRMED() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");

        manager.confirm(action.getActionId());

        assertThat(manager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.CONFIRMED);
    }

    @Test
    void 过期Action置EXPIRED并拦截后续操作() throws InterruptedException {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u", 1L);
        Thread.sleep(10);

        assertThatThrownBy(() -> manager.confirm(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("过期");
        assertThat(manager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.EXPIRED);
    }

    // ============ 风险规则 ============

    @Test
    void 高风险操作创建直接拒绝() {
        assertThatThrownBy(() -> manager.create(ActionType.BULK_UPDATE_DEVICES, "device", "all", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("高风险");
        assertThatThrownBy(() -> manager.create(ActionType.DELETE_DEVICE, "device", "lamp001", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("高风险");
        assertThatThrownBy(() -> manager.create(ActionType.UNBIND_DEVICE, "device", "lamp001", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("高风险");
    }

    @Test
    void 批量关闭创建进入待确认状态() {
        AgentAction action = manager.create(ActionType.TURN_OFF_ALL, "device", "all", Map.of(), "u");

        assertThat(action.getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
        assertThat(action.getRiskLevel()).isEqualTo(ActionRisk.LOW_WRITE);
    }

    @Test
    void 配置类操作创建进入待确认状态() {
        AgentAction threshold = manager.create(ActionType.UPDATE_LUX_THRESHOLD, "config", "system", Map.of("value", 150), "u");
        assertThat(threshold.getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
        assertThat(threshold.getRiskLevel()).isEqualTo(ActionRisk.LOW_WRITE);
        assertThat(threshold.getTargetType()).isEqualTo("config");

        AgentAction auto = manager.create(ActionType.UPDATE_AUTO_MODE, "config", "system", Map.of("enabled", false), "u");
        assertThat(auto.getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
    }

    @Test
    void 阈值参数范围按后端规则10到500校验() {
        assertThatThrownBy(() -> manager.create(ActionType.UPDATE_LUX_THRESHOLD, "config", "system", Map.of("value", 5), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("10-500");
        assertThatThrownBy(() -> manager.create(ActionType.UPDATE_LUX_THRESHOLD, "config", "system", Map.of("value", 600), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("10-500");
        assertThatThrownBy(() -> manager.create(ActionType.UPDATE_LUX_THRESHOLD, "config", "system", Map.of("value", "abc"), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("10-500");
    }

    @Test
    void 自动模式参数必须布尔() {
        assertThatThrownBy(() -> manager.create(ActionType.UPDATE_AUTO_MODE, "config", "system", Map.of("enabled", "yes"), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("enabled");
    }

    @Test
    void READ类创建即CONFIRMED无需确认() {
        AgentAction action = manager.create(ActionType.QUERY_DEVICES, "device", "lamp001", Map.of(), "u");

        assertThat(action.getStatus()).isEqualTo(ActionStatus.CONFIRMED);
        assertThat(action.getRiskLevel()).isEqualTo(ActionRisk.READ);
    }

    // ============ 参数安全边界 ============

    @Test
    void 万能命令参数键拒绝() {
        assertThatThrownBy(() -> manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001",
                Map.of("command", "rm -rf /"), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("command");
        assertThatThrownBy(() -> manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001",
                Map.of("sql", "DROP TABLE device"), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("sql");
        assertThatThrownBy(() -> manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001",
                Map.of("payload", "{\"on\":true}"), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("payload");
        assertThatThrownBy(() -> manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001",
                Map.of("topic", "device/+/cmd"), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("topic");
    }

    @Test
    void 未知参数键拒绝() {
        assertThatThrownBy(() -> manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001",
                Map.of("interval", 5), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("不允许的参数");
    }

    @Test
    void 目标参数校验() {
        assertThatThrownBy(() -> manager.create(ActionType.TURN_ON_LIGHT, "device", "  ", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("targetId");
        assertThatThrownBy(() -> manager.create(ActionType.TURN_ON_LIGHT, "mqtt", "lamp001", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("targetType");
    }

    // ============ 二次复核 revalidate（阶段17：确认/执行前参数防线） ============

    private AgentAction handAction(ActionType type, String targetType, String targetId, Map<String, Object> args) {
        AgentAction action = new AgentAction();
        action.setActionType(type);
        action.setTargetType(targetType);
        action.setTargetId(targetId);
        action.setArguments(args);
        return action;
    }

    @Test
    void 复核时万能命令参数键仍被拒绝() {
        AgentAction action = handAction(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of("command", "rm -rf /"));

        assertThatThrownBy(() -> manager.revalidate(action))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("command");
    }

    @Test
    void 复核时未知参数键仍被拒绝() {
        AgentAction action = handAction(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of("interval", 5));

        assertThatThrownBy(() -> manager.revalidate(action))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("不允许的参数");
    }

    @Test
    void 复核时非法目标仍被拒绝() {
        assertThatThrownBy(() -> manager.revalidate(handAction(ActionType.TURN_ON_LIGHT, "mqtt", "lamp001", Map.of())))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("targetType");
        assertThatThrownBy(() -> manager.revalidate(handAction(ActionType.TURN_ON_LIGHT, "device", " ", Map.of())))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("targetId");
    }

    @Test
    void 合法Action复核通过() {
        manager.revalidate(handAction(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of()));
        manager.revalidate(handAction(ActionType.TURN_ON_LIGHT, "device", "lamp002", Map.of()));
    }

    // ============ 非法状态流转 ============

    @Test
    void 已确认Action不可重复确认() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");
        manager.confirm(action.getActionId());

        assertThatThrownBy(() -> manager.confirm(action.getActionId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 已确认Action不可再取消() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");
        manager.confirm(action.getActionId());

        assertThatThrownBy(() -> manager.cancel(action.getActionId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 执行中可标记COMMAND_ACCEPTED终态() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");
        manager.confirm(action.getActionId());
        manager.markExecuting(action.getActionId());
        manager.markAccepted(action.getActionId(), "COMMAND_ACCEPTED：控制指令已发送，但当前尚未获得设备执行确认");

        assertThat(manager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.COMMAND_ACCEPTED);
        assertThat(manager.find(action.getActionId()).orElseThrow().getMessage())
                .contains("尚未获得设备执行确认");
    }

    // ============ 会话删除时的安全处理（阶段30） ============

    @Test
    void 取消指定会话的全部待确认Action且不影响其他会话() {
        AgentAction a1 = manager.create(ActionType.TURN_ON_LIGHT, "device", "lamp001", Map.of(), "u");
        a1.setConversationId("conv-a");
        AgentAction a2 = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp002", Map.of(), "u");
        a2.setConversationId("conv-a");
        AgentAction b1 = manager.create(ActionType.TURN_ON_LIGHT, "device", "lamp003", Map.of(), "u");
        b1.setConversationId("conv-b");

        int count = manager.cancelPendingByConversation("conv-a");

        assertThat(count).isEqualTo(2);
        assertThat(manager.find(a1.getActionId()).orElseThrow().getStatus()).isEqualTo(ActionStatus.CANCELLED);
        assertThat(manager.find(a2.getActionId()).orElseThrow().getStatus()).isEqualTo(ActionStatus.CANCELLED);
        // 其他会话的待确认 Action 不受影响
        assertThat(manager.find(b1.getActionId()).orElseThrow().getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
    }

    @Test
    void 会话删除只取消待确认状态不影响已确认或终态() {
        AgentAction pending = manager.create(ActionType.TURN_ON_LIGHT, "device", "lamp001", Map.of(), "u");
        pending.setConversationId("conv-a");
        AgentAction confirmed = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp002", Map.of(), "u");
        confirmed.setConversationId("conv-a");
        manager.confirm(confirmed.getActionId());

        int count = manager.cancelPendingByConversation("conv-a");

        assertThat(count).isEqualTo(1);
        assertThat(manager.find(pending.getActionId()).orElseThrow().getStatus()).isEqualTo(ActionStatus.CANCELLED);
        assertThat(manager.find(confirmed.getActionId()).orElseThrow().getStatus()).isEqualTo(ActionStatus.CONFIRMED);
    }

    @Test
    void 会话删除取消动作同样触发审计钩子() {
        AtomicInteger audits = new AtomicInteger();
        manager.setAuditHook(a -> audits.incrementAndGet());
        AgentAction action = manager.create(ActionType.TURN_ON_LIGHT, "device", "lamp001", Map.of(), "u");
        action.setConversationId("conv-a");

        manager.cancelPendingByConversation("conv-a");

        assertThat(audits).hasValue(1);
        assertThat(action.getStatus()).isEqualTo(ActionStatus.CANCELLED);
        assertThat(action.getMessage()).contains("会话已删除");
    }

    @Test
    void 不存在的Action操作报错() {
        assertThatThrownBy(() -> manager.confirm("no-such-id"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("不存在");
        assertThatThrownBy(() -> manager.cancel("no-such-id"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("不存在");
    }
}
