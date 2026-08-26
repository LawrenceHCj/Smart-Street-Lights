package com.smartlamp.agent.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        assertThatThrownBy(() -> manager.create(ActionType.TURN_OFF_ALL, "device", "all", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("高风险");
        assertThatThrownBy(() -> manager.create(ActionType.BULK_UPDATE_DEVICES, "device", "all", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("高风险");
        assertThatThrownBy(() -> manager.create(ActionType.DELETE_DEVICE, "device", "lamp001", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("高风险");
        assertThatThrownBy(() -> manager.create(ActionType.UNBIND_DEVICE, "device", "lamp001", Map.of(), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("高风险");
    }

    @Test
    void 未开放操作创建直接拒绝() {
        assertThatThrownBy(() -> manager.create(ActionType.UPDATE_LUX_THRESHOLD, "config", "system", Map.of("value", 30), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("未开放");
        assertThatThrownBy(() -> manager.create(ActionType.UPDATE_AUTO_MODE, "config", "system", Map.of("enabled", false), "u"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("未开放");
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
    void 不存在的Action操作报错() {
        assertThatThrownBy(() -> manager.confirm("no-such-id"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("不存在");
        assertThatThrownBy(() -> manager.cancel("no-such-id"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("不存在");
    }
}
