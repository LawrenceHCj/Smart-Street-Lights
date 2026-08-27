package com.smartlamp.service;

import com.smartlamp.dto.CommandStatus;
import com.smartlamp.dto.ControlOutcome;
import com.smartlamp.entity.Device;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

// 单设备开关灯控制 Service（阶段18，破例代 3号 实现：网页按钮与 Agent 复用同一入口）。
// 调用链：调用方 → 本 Service → MqttPublisherService（命令下发）/ DeviceService（状态读取与乐观更新）。
// 执行结果必须区分 COMMAND_ACCEPTED / DEVICE_CONFIRMED / FAILED / TIMEOUT：
// 当前没有 4号 的命令确认反馈链路，成功一律如实返回 COMMAND_ACCEPTED（命令已发送 ≠ 设备已执行）。
@Service
public class DeviceControlService {

    private static final long CONFIRM_POLL_INTERVAL_MS = 200;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    // 设备执行确认等待器（4号 反馈链路就绪后注入；当前为 null，不等待）
    private DeviceConfirmationWaiter confirmationWaiter;

    // 等待设备确认的超时时间（毫秒）；0 表示不等待（当前默认 0，后续配置化）
    private long confirmationTimeoutMs = 0;

    // 开灯（单设备）：Agent 白名单 TURN_ON_LIGHT 对应的正式执行入口
    public ControlOutcome turnOnLight(String deviceId) {
        return control(deviceId, "ON");
    }

    // 关灯（单设备）：Agent 白名单 TURN_OFF_LIGHT 对应的正式执行入口
    public ControlOutcome turnOffLight(String deviceId) {
        return control(deviceId, "OFF");
    }

    private ControlOutcome control(String deviceId, String action) {
        long issuedAt = System.currentTimeMillis();

        // 1. 设备存在 / 绑定 / 在线检查（不通过一律 FAILED，绝不发命令）
        Device device = deviceService.getDeviceByCode(deviceId);
        if (device == null) return ControlOutcome.failed("设备不存在: " + deviceId);
        if (!Boolean.TRUE.equals(device.getBound())) return ControlOutcome.failed("设备未绑定: " + deviceId);
        if (!"ONLINE".equals(device.getStatus())) {
            return ControlOutcome.failed("设备离线: " + deviceId + "（当前状态: " + device.getStatus() + "）");
        }

        // 2. 下发 MQTT 控制命令（发布失败/异常 → FAILED，绝不谎报成功）
        String commandId = "CMD-" + issuedAt + "-" + UUID.randomUUID().toString().substring(0, 8);
        String topic = "device/" + deviceId + "/cmd";
        String payload = "{\"deviceId\":\"" + deviceId + "\",\"action\":\"" + action + "\"}";
        try {
            if (!mqttPublisherService.publish(topic, payload)) {
                return ControlOutcome.failed("MQTT 发布失败: " + topic);
            }
        } catch (Exception e) {
            return ControlOutcome.failed("MQTT 发布异常: " + e.getMessage());
        }

        // 3. 后端乐观更新（与网页控制行为一致；真实状态以设备后续 data 上报为准）
        deviceService.updateLampStatus(device, action);

        // 4. 设备执行确认等待：当前无确认通道 → 如实返回 COMMAND_ACCEPTED；
        //    4号 反馈链路就绪并注入 waiter 后 → DEVICE_CONFIRMED / TIMEOUT
        if (confirmationWaiter != null && confirmationTimeoutMs > 0) {
            long deadline = issuedAt + confirmationTimeoutMs;
            while (System.currentTimeMillis() < deadline) {
                if (confirmationWaiter.isConfirmed(deviceId, action)) {
                    return ControlOutcome.confirmed(commandId, deviceId, action, issuedAt, "设备已确认执行");
                }
                try {
                    Thread.sleep(CONFIRM_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return ControlOutcome.timeout(commandId, deviceId, action, issuedAt,
                    "控制指令已发送，但设备在 " + confirmationTimeoutMs + " 毫秒内未确认执行");
        }
        return ControlOutcome.accepted(commandId, deviceId, action, issuedAt,
                "控制指令已发送，但当前尚未获得设备执行确认");
    }

    // 以下 setter 供测试与后续 4号 反馈链路接入使用（当前生产环境不注入）
    public void setConfirmationWaiter(DeviceConfirmationWaiter waiter) {
        this.confirmationWaiter = waiter;
    }

    public void setConfirmationTimeoutMs(long confirmationTimeoutMs) {
        this.confirmationTimeoutMs = confirmationTimeoutMs;
    }
}
