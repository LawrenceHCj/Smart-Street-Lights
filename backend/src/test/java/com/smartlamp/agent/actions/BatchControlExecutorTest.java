package com.smartlamp.agent.actions;

import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.service.DeviceCommandService;
import com.smartlamp.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 批量控制执行器单测（权限调整后开放"关闭全部设备"/"打开全部设备"，均需二次确认）：
// 只对在线且已绑定设备逐台下发（与网页同一 DeviceCommandService），按命令表回执如实聚合
@ExtendWith(MockitoExtension.class)
class BatchControlExecutorTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceCommandService deviceCommandService;

    private ActionManager actionManager;
    private ActionGateway actionGateway;

    private BatchControlExecutor executorWith(long ackTimeoutMs) {
        return new BatchControlExecutor(actionGateway, deviceService, deviceCommandService, ackTimeoutMs);
    }

    private Device onlineBoundDevice(String code) {
        Device device = new Device();
        device.setCode(code);
        device.setBound(true);
        device.setStatus("ONLINE");
        return device;
    }

    private DeviceCommand command(String id, String code, CommandStatus status) {
        DeviceCommand command = new DeviceCommand();
        command.setCommandId(id);
        command.setDeviceCode(code);
        command.setAction("OFF");
        command.setStatus(status);
        return command;
    }

    @BeforeEach
    void setUp() {
        actionManager = new ActionManager();
        actionGateway = new ActionGateway();
        ReflectionTestUtils.setField(actionGateway, "actionManager", actionManager);
    }

    private AgentAction confirmedBatchAction(ActionType type) {
        AgentAction action = actionManager.create(type, "device", "all", Map.of(), "test-user");
        actionManager.confirm(action.getActionId());
        return action;
    }

    @Test
    void 无在线设备时FAILED不执行() {
        when(deviceService.getAllDevices()).thenReturn(List.of());
        AgentAction action = confirmedBatchAction(ActionType.TURN_OFF_ALL);

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.FAILED);
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    @Test
    void 下发N台无回执时如实COMMAND_ACCEPTED() {
        when(deviceService.getAllDevices()).thenReturn(List.of(
                onlineBoundDevice("lamp001"), onlineBoundDevice("lamp002")));
        when(deviceCommandService.dispatch("lamp001", "OFF", "AGENT")).thenReturn(command("CMD-1", "lamp001", CommandStatus.DISPATCHED));
        when(deviceCommandService.dispatch("lamp002", "OFF", "AGENT")).thenReturn(command("CMD-2", "lamp002", CommandStatus.DISPATCHED));
        AgentAction action = confirmedBatchAction(ActionType.TURN_OFF_ALL);

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.COMMAND_ACCEPTED);
        assertThat(result.message()).contains("共下发 2 台关闭命令").contains("尚未获得设备执行确认");
    }

    @Test
    void 全部确认执行时DEVICE_CONFIRMED() {
        when(deviceService.getAllDevices()).thenReturn(List.of(onlineBoundDevice("lamp001")));
        when(deviceCommandService.dispatch("lamp001", "OFF", "AGENT")).thenReturn(command("CMD-1", "lamp001", CommandStatus.DISPATCHED));
        when(deviceCommandService.find("CMD-1")).thenReturn(command("CMD-1", "lamp001", CommandStatus.SUCCESS));
        AgentAction action = confirmedBatchAction(ActionType.TURN_OFF_ALL);

        ExecutorResult result = executorWith(5000).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.DEVICE_CONFIRMED);
        assertThat(result.message()).contains("批量关闭完成");
    }

    @Test
    void 部分失败时FAILED且如实说明() {
        when(deviceService.getAllDevices()).thenReturn(List.of(onlineBoundDevice("lamp001")));
        when(deviceCommandService.dispatch("lamp001", "OFF", "AGENT")).thenReturn(command("CMD-1", "lamp001", CommandStatus.DISPATCHED));
        when(deviceCommandService.find("CMD-1")).thenReturn(command("CMD-1", "lamp001", CommandStatus.FAILED));
        AgentAction action = confirmedBatchAction(ActionType.TURN_OFF_ALL);

        ExecutorResult result = executorWith(5000).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.FAILED);
        assertThat(result.message()).contains("1 台执行失败");
    }

    @Test
    void 离线设备不参与批量下发() {
        Device offline = onlineBoundDevice("lamp002");
        offline.setStatus("OFFLINE");
        when(deviceService.getAllDevices()).thenReturn(List.of(
                onlineBoundDevice("lamp001"), offline));
        when(deviceCommandService.dispatch("lamp001", "OFF", "AGENT")).thenReturn(command("CMD-1", "lamp001", CommandStatus.DISPATCHED));
        AgentAction action = confirmedBatchAction(ActionType.TURN_OFF_ALL);

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.message()).contains("共下发 1 台关闭命令"); // 离线设备跳过
    }

    @Test
    void 未确认Action绝不批量执行() {
        AgentAction action = actionManager.create(ActionType.TURN_OFF_ALL, "device", "all", Map.of(), "test-user");

        assertThatThrownBy(() -> actionGateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class);
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    // ============ 批量开灯（TURN_ON_ALL，对称开放） ============

    @Test
    void 批量开灯下发ON命令并按回执聚合() {
        when(deviceService.getAllDevices()).thenReturn(List.of(onlineBoundDevice("lamp001")));
        when(deviceCommandService.dispatch("lamp001", "ON", "AGENT"))
                .thenReturn(command("CMD-1", "lamp001", CommandStatus.DISPATCHED));
        when(deviceCommandService.find("CMD-1")).thenReturn(command("CMD-1", "lamp001", CommandStatus.SUCCESS));
        AgentAction action = confirmedBatchAction(ActionType.TURN_ON_ALL);

        ExecutorResult result = executorWith(5000).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.DEVICE_CONFIRMED);
        assertThat(result.message()).contains("批量打开完成").contains("共下发 1 台打开命令");
    }

    @Test
    void 批量开灯无回执时如实COMMAND_ACCEPTED() {
        when(deviceService.getAllDevices()).thenReturn(List.of(onlineBoundDevice("lamp001")));
        when(deviceCommandService.dispatch("lamp001", "ON", "AGENT"))
                .thenReturn(command("CMD-1", "lamp001", CommandStatus.DISPATCHED));
        AgentAction action = confirmedBatchAction(ActionType.TURN_ON_ALL);

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.COMMAND_ACCEPTED);
        assertThat(result.message()).contains("批量打开命令已下发").contains("尚未获得设备执行确认");
    }

    @Test
    void 未确认的批量开灯绝不执行() {
        AgentAction action = actionManager.create(ActionType.TURN_ON_ALL, "device", "all", Map.of(), "test-user");

        assertThatThrownBy(() -> actionGateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class);
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    // ============ 批量控制区域限定 ============

    @Test
    void 区域限定批量只下发匹配区域的设备() {
        Device a = onlineBoundDevice("lamp001");
        a.setLocation("北门入口");
        Device b = onlineBoundDevice("lamp003");
        b.setLocation("东门");
        when(deviceService.getAllDevices()).thenReturn(List.of(a, b));
        when(deviceCommandService.dispatch("lamp003", "OFF", "AGENT"))
                .thenReturn(command("CMD-1", "lamp003", CommandStatus.DISPATCHED));
        AgentAction action = actionManager.create(ActionType.TURN_OFF_ALL, "device", "东门",
                Map.of("locationKeyword", "东门"), "test-user");
        actionManager.confirm(action.getActionId());

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.COMMAND_ACCEPTED);
        assertThat(result.message()).contains("共下发 1 台关闭命令");
        // 非匹配区域的设备绝不下发
        verify(deviceCommandService, never()).dispatch(org.mockito.ArgumentMatchers.eq("lamp001"), any(), any());
    }

    @Test
    void 区域限定批量确认时已无匹配设备FAILED() {
        when(deviceService.getAllDevices()).thenReturn(List.of(onlineBoundDevice("lamp001")));
        AgentAction action = actionManager.create(ActionType.TURN_OFF_ALL, "device", "东门",
                Map.of("locationKeyword", "东门"), "test-user");
        actionManager.confirm(action.getActionId());

        ExecutorResult result = executorWith(0).execute(action);

        assertThat(result.status()).isEqualTo(com.smartlamp.dto.CommandStatus.FAILED);
        assertThat(result.message()).contains("东门");
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }
}
