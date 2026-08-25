package com.smartlamp.agent.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 阶段15：ActionGateway 执行前安全检查单测（纯内存，不依赖 Spring/MySQL/MQTT）
class ActionGatewayTest {

    private ActionManager manager;
    private ActionGateway gateway;

    @BeforeEach
    void setUp() {
        manager = new ActionManager();
        gateway = new ActionGateway();
        ReflectionTestUtils.setField(gateway, "actionManager", manager);
    }

    // ============ 未确认 Action 绝不允许执行 ============

    @Test
    void 未确认Action执行被拒绝() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");

        assertThatThrownBy(() -> gateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("未确认");
        // 状态仍为 PENDING_CONFIRMATION，未进入执行态
        assertThat(manager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.PENDING_CONFIRMATION);
    }

    @Test
    void 未确认时业务执行器绝不被调用() {
        AtomicInteger calls = new AtomicInteger();
        gateway.registerExecutor(ActionType.TURN_OFF_LIGHT, a -> calls.incrementAndGet());
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");

        assertThatThrownBy(() -> gateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class);
        assertThat(calls).hasValue(0); // 安全红线：执行器零调用
    }

    // ============ 合法执行链路 ============

    @Test
    void 确认后执行进入SUCCESS() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");
        manager.confirm(action.getActionId());

        AgentAction result = gateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(result.getMessage()).contains("安全网关");
    }

    @Test
    void READ类免确认直接执行成功() {
        AgentAction action = manager.create(ActionType.QUERY_DEVICES, "device", "lamp001", Map.of(), "u");

        AgentAction result = gateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
    }

    @Test
    void 注册执行器后确认执行会调用执行器() {
        AtomicInteger calls = new AtomicInteger();
        gateway.registerExecutor(ActionType.TURN_OFF_LIGHT, a -> calls.incrementAndGet());
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");
        manager.confirm(action.getActionId());

        AgentAction result = gateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(calls).hasValue(1);
    }

    @Test
    void 执行器抛异常时Action置FAILED() {
        gateway.registerExecutor(ActionType.TURN_OFF_LIGHT, a -> {
            throw new RuntimeException("设备无响应");
        });
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");
        manager.confirm(action.getActionId());

        assertThatThrownBy(() -> gateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("执行失败");
        assertThat(manager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.FAILED);
    }

    // ============ 其他拦截场景 ============

    @Test
    void 已取消Action执行被拒绝() {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u");
        manager.cancel(action.getActionId());

        assertThatThrownBy(() -> gateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("未确认");
    }

    @Test
    void 已过期Action执行被拒绝() throws InterruptedException {
        AgentAction action = manager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001", Map.of(), "u", 1L);
        Thread.sleep(10);

        assertThatThrownBy(() -> gateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("过期");
        assertThat(manager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.EXPIRED);
    }

    @Test
    void 不存在的Action执行被拒绝() {
        assertThatThrownBy(() -> gateway.execute("no-such-id"))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("不存在");
    }

    @Test
    void 高风险操作不允许注册执行器() {
        assertThatThrownBy(() -> gateway.registerExecutor(ActionType.TURN_OFF_ALL, a -> {
        }))
                .isInstanceOf(ActionRejectedException.class).hasMessageContaining("高风险");
    }
}
