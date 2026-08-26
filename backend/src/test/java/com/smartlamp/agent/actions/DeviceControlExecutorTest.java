package com.smartlamp.agent.actions;

import com.smartlamp.dto.ControlOutcome;
import com.smartlamp.service.DeviceControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 阶段18：真实控制执行器单测——白名单 Action → 3号 DeviceControlService 的映射与结果如实报告
@ExtendWith(MockitoExtension.class)
class DeviceControlExecutorTest {

    @Mock
    private DeviceControlService deviceControlService;

    private ActionManager actionManager;
    private ActionGateway actionGateway;

    @BeforeEach
    void setUp() {
        actionManager = new ActionManager();
        actionGateway = new ActionGateway();
        ReflectionTestUtils.setField(actionGateway, "actionManager", actionManager);
        new DeviceControlExecutor(actionGateway, deviceControlService);
    }

    private AgentAction confirmedAction(ActionType type, String code) {
        AgentAction action = actionManager.create(type, "device", code, Map.of(), "test-user");
        actionManager.confirm(action.getActionId());
        return action;
    }

    // ============ 映射与如实报告 ============

    @Test
    void 开灯执行调用turnOnLight且未获回执时置COMMAND_ACCEPTED绝不标SUCCESS() {
        when(deviceControlService.turnOnLight("lamp001")).thenReturn(
                ControlOutcome.accepted("CMD-1", "lamp001", "ON", 1000L, "控制指令已发送，但当前尚未获得设备执行确认"));
        AgentAction action = confirmedAction(ActionType.TURN_ON_LIGHT, "lamp001");

        AgentAction result = actionGateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.COMMAND_ACCEPTED);
        assertThat(result.getMessage())
                .contains("COMMAND_ACCEPTED")
                .contains("尚未获得设备执行确认")
                .contains("CMD-1");
        verify(deviceControlService).turnOnLight("lamp001");
        verify(deviceControlService, never()).turnOffLight("lamp001");
    }

    @Test
    void 关灯执行调用turnOffLight且未获回执时置COMMAND_ACCEPTED() {
        when(deviceControlService.turnOffLight("lamp002")).thenReturn(
                ControlOutcome.accepted("CMD-2", "lamp002", "OFF", 1000L, "控制指令已发送，但当前尚未获得设备执行确认"));
        AgentAction action = confirmedAction(ActionType.TURN_OFF_LIGHT, "lamp002");

        AgentAction result = actionGateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.COMMAND_ACCEPTED);
        assertThat(result.getMessage()).contains("COMMAND_ACCEPTED");
        verify(deviceControlService).turnOffLight("lamp002");
        verify(deviceControlService, never()).turnOnLight("lamp002");
    }

    @Test
    void DEVICE_CONFIRMED时消息如实标注已确认() {
        when(deviceControlService.turnOnLight("lamp001")).thenReturn(
                ControlOutcome.confirmed("CMD-3", "lamp001", "ON", 1000L, "设备已确认执行"));
        AgentAction action = confirmedAction(ActionType.TURN_ON_LIGHT, "lamp001");

        AgentAction result = actionGateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(result.getMessage()).contains("DEVICE_CONFIRMED").contains("已确认执行");
    }

    // ============ Service 失败 / 超时 → Action FAILED ============

    @Test
    void Service返回FAILED时Action置FAILED且消息如实() {
        when(deviceControlService.turnOnLight("lamp001")).thenReturn(
                ControlOutcome.failed("设备离线: lamp001（当前状态: OFFLINE）"));
        AgentAction action = confirmedAction(ActionType.TURN_ON_LIGHT, "lamp001");

        assertThatThrownBy(() -> actionGateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("FAILED").hasMessageContaining("离线");
        AgentAction failed = actionManager.find(action.getActionId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(failed.getMessage()).contains("FAILED");
    }

    @Test
    void 执行超时TIMEOUT时Action置FAILED且消息如实() {
        when(deviceControlService.turnOffLight("lamp001")).thenReturn(
                ControlOutcome.timeout("CMD-4", "lamp001", "OFF", 1000L,
                        "控制指令已发送，但设备在 5000 毫秒内未确认执行"));
        AgentAction action = confirmedAction(ActionType.TURN_OFF_LIGHT, "lamp001");

        assertThatThrownBy(() -> actionGateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("TIMEOUT").hasMessageContaining("未确认执行");
        AgentAction failed = actionManager.find(action.getActionId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(failed.getMessage()).contains("TIMEOUT");
    }

    // ============ 安全红线 ============

    @Test
    void 未确认Action绝不调用正式控制Service() {
        AgentAction action = actionManager.create(ActionType.TURN_ON_LIGHT, "device", "lamp001", Map.of(), "test-user");

        assertThatThrownBy(() -> actionGateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class);
        verify(deviceControlService, never()).turnOnLight("lamp001");
    }
}
