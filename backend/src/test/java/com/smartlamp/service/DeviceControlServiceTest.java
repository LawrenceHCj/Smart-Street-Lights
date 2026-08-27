package com.smartlamp.service;

import com.smartlamp.dto.CommandStatus;
import com.smartlamp.dto.ControlOutcome;
import com.smartlamp.entity.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 阶段18：单设备开关灯控制 Service 单测（Mockito，不依赖 MySQL/MQTT）
// 覆盖：在线开/关成功、设备离线、重复开/关、Service 返回失败、执行超时
@ExtendWith(MockitoExtension.class)
class DeviceControlServiceTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private MqttPublisherService mqttPublisherService;

    private DeviceControlService controlService;

    @BeforeEach
    void setUp() {
        controlService = new DeviceControlService();
        ReflectionTestUtils.setField(controlService, "deviceService", deviceService);
        ReflectionTestUtils.setField(controlService, "mqttPublisherService", mqttPublisherService);
    }

    private Device onlineDevice(String code, String lampStatus) {
        Device device = new Device();
        device.setCode(code);
        device.setBound(true);
        device.setStatus("ONLINE");
        device.setLampStatus(lampStatus);
        return device;
    }

    // ============ 在线设备开灯 / 关灯成功 ============

    @Test
    void 在线设备开灯成功返回COMMAND_ACCEPTED且如实说明未获确认() {
        Device device = onlineDevice("lamp001", "OFF");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish("device/lamp001/cmd",
                "{\"deviceId\":\"lamp001\",\"action\":\"ON\"}")).thenReturn(true);

        ControlOutcome outcome = controlService.turnOnLight("lamp001");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.COMMAND_ACCEPTED);
        assertThat(outcome.getCommandId()).startsWith("CMD-");
        assertThat(outcome.getAction()).isEqualTo("ON");
        assertThat(outcome.getMessage()).contains("尚未获得设备执行确认");
        verify(deviceService).updateLampStatus(device, "ON");
    }

    @Test
    void 在线设备关灯成功返回COMMAND_ACCEPTED() {
        Device device = onlineDevice("lamp001", "ON");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish("device/lamp001/cmd",
                "{\"deviceId\":\"lamp001\",\"action\":\"OFF\"}")).thenReturn(true);

        ControlOutcome outcome = controlService.turnOffLight("lamp001");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.COMMAND_ACCEPTED);
        assertThat(outcome.getAction()).isEqualTo("OFF");
        verify(deviceService).updateLampStatus(device, "OFF");
    }

    // ============ 设备离线 / 不存在 / 未绑定 ============

    @Test
    void 设备离线返回FAILED且不发命令不更新状态() {
        Device device = onlineDevice("lamp003", "OFF");
        device.setStatus("OFFLINE");
        when(deviceService.getDeviceByCode("lamp003")).thenReturn(device);

        ControlOutcome outcome = controlService.turnOnLight("lamp003");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(outcome.getMessage()).contains("离线");
        verify(mqttPublisherService, never()).publish(any(), any());
        verify(deviceService, never()).updateLampStatus(any(), any());
    }

    @Test
    void 设备不存在返回FAILED() {
        when(deviceService.getDeviceByCode("lamp999")).thenReturn(null);

        ControlOutcome outcome = controlService.turnOnLight("lamp999");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(outcome.getMessage()).contains("不存在");
        verify(mqttPublisherService, never()).publish(any(), any());
    }

    @Test
    void 设备未绑定返回FAILED() {
        Device device = onlineDevice("lamp001", "OFF");
        device.setBound(false);
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);

        ControlOutcome outcome = controlService.turnOnLight("lamp001");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(outcome.getMessage()).contains("未绑定");
        verify(mqttPublisherService, never()).publish(any(), any());
    }

    // ============ 重复开灯 / 重复关灯（服务层与网页行为一致，可重复下发） ============

    @Test
    void 重复开灯再次调用仍返回COMMAND_ACCEPTED() {
        Device device = onlineDevice("lamp001", "OFF");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish("device/lamp001/cmd",
                "{\"deviceId\":\"lamp001\",\"action\":\"ON\"}")).thenReturn(true);

        ControlOutcome first = controlService.turnOnLight("lamp001");
        ControlOutcome second = controlService.turnOnLight("lamp001");

        assertThat(first.getStatus()).isEqualTo(CommandStatus.COMMAND_ACCEPTED);
        assertThat(second.getStatus()).isEqualTo(CommandStatus.COMMAND_ACCEPTED);
        verify(mqttPublisherService, times(2)).publish("device/lamp001/cmd",
                "{\"deviceId\":\"lamp001\",\"action\":\"ON\"}");
    }

    @Test
    void 重复关灯再次调用仍返回COMMAND_ACCEPTED() {
        Device device = onlineDevice("lamp001", "ON");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish("device/lamp001/cmd",
                "{\"deviceId\":\"lamp001\",\"action\":\"OFF\"}")).thenReturn(true);

        assertThat(controlService.turnOffLight("lamp001").getStatus())
                .isEqualTo(CommandStatus.COMMAND_ACCEPTED);
        assertThat(controlService.turnOffLight("lamp001").getStatus())
                .isEqualTo(CommandStatus.COMMAND_ACCEPTED);
    }

    // ============ Service 返回失败 ============

    @Test
    void MQTT发布返回false时返回FAILED且不更新状态() {
        Device device = onlineDevice("lamp001", "OFF");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish(any(), any())).thenReturn(false);

        ControlOutcome outcome = controlService.turnOnLight("lamp001");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(outcome.getMessage()).contains("发布失败");
        verify(deviceService, never()).updateLampStatus(any(), any());
    }

    @Test
    void MQTT发布抛异常时返回FAILED且不更新状态() {
        Device device = onlineDevice("lamp001", "OFF");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish(any(), any()))
                .thenThrow(new RuntimeException("连接已断开"));

        ControlOutcome outcome = controlService.turnOnLight("lamp001");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(outcome.getMessage()).contains("发布异常");
        verify(deviceService, never()).updateLampStatus(any(), any());
    }

    // ============ 执行超时 / 设备确认 ============

    @Test
    void 等待确认超时返回TIMEOUT() {
        Device device = onlineDevice("lamp001", "OFF");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish(any(), any())).thenReturn(true);
        controlService.setConfirmationWaiter((deviceId, status) -> false); // 设备始终未确认
        controlService.setConfirmationTimeoutMs(300);

        ControlOutcome outcome = controlService.turnOnLight("lamp001");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.TIMEOUT);
        assertThat(outcome.getMessage()).contains("未确认执行");
    }

    @Test
    void 设备确认执行返回DEVICE_CONFIRMED() {
        Device device = onlineDevice("lamp001", "OFF");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish(any(), any())).thenReturn(true);
        controlService.setConfirmationWaiter((deviceId, status) -> true);
        controlService.setConfirmationTimeoutMs(1000);

        ControlOutcome outcome = controlService.turnOnLight("lamp001");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.DEVICE_CONFIRMED);
        assertThat(outcome.getMessage()).contains("已确认执行");
    }

    @Test
    void 超时未配置时不等待直接返回COMMAND_ACCEPTED() {
        Device device = onlineDevice("lamp001", "OFF");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(device);
        when(mqttPublisherService.publish(any(), any())).thenReturn(true);
        controlService.setConfirmationWaiter((deviceId, status) -> true);
        controlService.setConfirmationTimeoutMs(0);

        ControlOutcome outcome = controlService.turnOnLight("lamp001");

        assertThat(outcome.getStatus()).isEqualTo(CommandStatus.COMMAND_ACCEPTED);
    }
}
