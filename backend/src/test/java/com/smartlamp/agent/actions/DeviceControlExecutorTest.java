package com.smartlamp.agent.actions;

import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.service.DeviceCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 整合修复#5：控制执行器单测——Agent 与网页统一走 DeviceCommandService（命令表），
// 按命令表真实状态如实报告：SUCCESS 才 DEVICE_CONFIRMED，未获回执只能 COMMAND_ACCEPTED
@ExtendWith(MockitoExtension.class)
class DeviceControlExecutorTest {

    @Mock
    private DeviceCommandService deviceCommandService;

    private ActionManager actionManager;
    private ActionGateway actionGateway;

    // 回执等待窗口：默认 5 秒；置 0 时执行器不等待直接报 COMMAND_ACCEPTED
    private DeviceControlExecutor executorWith(long ackTimeoutMs) {
        return new DeviceControlExecutor(actionGateway, deviceCommandService, ackTimeoutMs);
    }

    private DeviceCommand command(CommandStatus status) {
        DeviceCommand command = new DeviceCommand();
        command.setCommandId("CMD-1");
        command.setDeviceCode("lamp001");
        command.setAction("ON");
        command.setStatus(status);
        return command;
    }

    @BeforeEach
    void setUp() {
        actionManager = new ActionManager();
        actionGateway = new ActionGateway();
        ReflectionTestUtils.setField(actionGateway, "actionManager", actionManager);
    }

    private AgentAction confirmedAction(ActionType type, String code) {
        AgentAction action = actionManager.create(type, "device", code, Map.of(), "test-user");
        actionManager.confirm(action.getActionId());
        return action;
    }

    // ============ 与网页统一走 DeviceCommandService ============

    @Test
    void 开灯执行调用dispatch且无回执时如实COMMAND_ACCEPTED() {
        when(deviceCommandService.dispatch("lamp001", "ON", "AGENT")).thenReturn(command(CommandStatus.DISPATCHED));
        AgentAction action = confirmedAction(ActionType.TURN_ON_LIGHT, "lamp001");

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.COMMAND_ACCEPTED);
        assertThat(result.message()).contains("COMMAND_ACCEPTED").contains("CMD-1");
        verify(deviceCommandService).dispatch("lamp001", "ON", "AGENT");
    }

    @Test
    void 关灯执行调用dispatch并映射OFF() {
        DeviceCommand off = command(CommandStatus.DISPATCHED);
        off.setAction("OFF");
        when(deviceCommandService.dispatch("lamp002", "OFF", "AGENT")).thenReturn(off);
        AgentAction action = confirmedAction(ActionType.TURN_OFF_LIGHT, "lamp002");

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.COMMAND_ACCEPTED);
        verify(deviceCommandService).dispatch("lamp002", "OFF", "AGENT");
    }

    // ============ 按命令表真实状态报告 ============

    @Test
    void 收到SUCCESS回执时DEVICE_CONFIRMED() {
        when(deviceCommandService.dispatch(any(), any(), any())).thenReturn(command(CommandStatus.DISPATCHED));
        when(deviceCommandService.find("CMD-1")).thenReturn(command(CommandStatus.SUCCESS));
        AgentAction action = confirmedAction(ActionType.TURN_ON_LIGHT, "lamp001");

        ExecutorResult result = executorWith(5000).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.DEVICE_CONFIRMED);
        assertThat(result.message()).contains("DEVICE_CONFIRMED").contains("已确认执行");
    }

    @Test
    void 收到FAILED回执时如实FAILED() {
        when(deviceCommandService.dispatch(any(), any(), any())).thenReturn(command(CommandStatus.DISPATCHED));
        when(deviceCommandService.find("CMD-1")).thenReturn(command(CommandStatus.FAILED));
        AgentAction action = confirmedAction(ActionType.TURN_OFF_LIGHT, "lamp001");

        ExecutorResult result = executorWith(5000).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.FAILED);
        assertThat(result.message()).contains("FAILED");
    }

    @Test
    void 命令表已TIMEOUT时如实TIMEOUT() {
        when(deviceCommandService.dispatch(any(), any(), any())).thenReturn(command(CommandStatus.DISPATCHED));
        when(deviceCommandService.find("CMD-1")).thenReturn(command(CommandStatus.TIMEOUT));
        AgentAction action = confirmedAction(ActionType.TURN_ON_LIGHT, "lamp001");

        ExecutorResult result = executorWith(5000).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.TIMEOUT);
        assertThat(result.message()).contains("TIMEOUT");
    }

    @Test
    void dispatch失败时如实FAILED() {
        when(deviceCommandService.dispatch(any(), any(), any()))
                .thenThrow(new RuntimeException("设备不存在: lamp999"));
        AgentAction action = confirmedAction(ActionType.TURN_ON_LIGHT, "lamp999");

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.FAILED);
        assertThat(result.message()).contains("下发失败");
    }

    // ============ 安全红线 ============

    @Test
    void 未确认Action绝不调用正式控制Service() {
        AgentAction action = actionManager.create(ActionType.TURN_ON_LIGHT, "device", "lamp001", Map.of(), "test-user");

        assertThatThrownBy(() -> actionGateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class);
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }
}
