package com.smartlamp.mqtt;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.entity.enums.DeviceStatus;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.LightPointRepository;
import com.smartlamp.service.AlarmService;
import com.smartlamp.service.AutoControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MqttMessageListener {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private LightPointRepository lightPointRepository;

    @Autowired
    private DeviceCommandRepository deviceCommandRepository;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private AutoControlService autoControlService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 允许的最大时间偏差：5分钟
    private static final long MAX_TIME_SKEW_MS = 5 * 60 * 1000;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = (String) message.getPayload();
        System.out.println("收到 MQTT 消息，主题: " + topic + ", 内容: " + payload);

        try {
            JsonNode json = objectMapper.readTree(payload);
            String deviceId = json.has("deviceId") ? json.get("deviceId").asText() : extractDeviceId(topic);
            if (deviceId == null) return;

            if (topic.endsWith("/data")) {
                handleData(deviceId, json);
            } else if (topic.endsWith("/heartbeat")) {
                handleHeartbeat(deviceId, json);
            } else if (topic.endsWith("/cmd_ack")) {
                handleCommandAck(deviceId, json);
            }
        } catch (Exception e) {
            System.err.println("处理 MQTT 消息失败: " + e.getMessage());
        }
    }

    private void handleData(String deviceId, JsonNode json) {
        double lux = json.get("lux").asDouble();
        long ts = json.has("ts") ? json.get("ts").asLong() : System.currentTimeMillis();
        long now = System.currentTimeMillis();

        // 检查时间戳是否合理（未来或过去太远则忽略）
        if (ts > now + MAX_TIME_SKEW_MS || ts < now - MAX_TIME_SKEW_MS) {
            System.err.println("忽略时间偏差过大的消息: " + deviceId + " ts=" + ts);
            return;
        }

        Device device = deviceRepository.findByCode(deviceId).orElse(null);
        boolean wasOffline = (device == null) || DeviceStatus.OFFLINE.equals(device.getStatus());

        if (device == null) {
            device = new Device();
            device.setCode(deviceId);
            device.setLocation("未知位置");
            device.setStatus(DeviceStatus.ONLINE);
            device.setCreatedAt(LocalDateTime.now());
        } else {
            // 检查是否是旧消息：如果已有遥测时间且新 ts 不大于旧值，忽略
            if (device.getLastTelemetryAt() != null && ts <= device.getLastTelemetryAt()) {
                System.out.println("忽略旧遥测消息: " + deviceId + " ts=" + ts);
                return;
            }
        }

        device.setLatestLux(lux);
        device.setLastSeen(ts);          // 可更新最后心跳时间，根据业务可保留
        device.setLastTelemetryAt(ts);   // 更新最近遥测时间
        device.setStatus(DeviceStatus.ONLINE);
        deviceRepository.save(device);

        if (wasOffline) {
            alarmService.recoverOfflineAlarms(deviceId);
        }

        // 保存光照历史
        LightPoint point = new LightPoint();
        point.setDeviceCode(deviceId);
        point.setLux(lux);
        point.setTs(ts);
        point.setCreatedAt(LocalDateTime.now());
        point.setServerReceivedAt(LocalDateTime.now());  // 记录服务器接收时间
        lightPointRepository.save(point);

        autoControlService.handleLightData(deviceId, lux);
    }

    private void handleHeartbeat(String deviceId, JsonNode json) {
        long ts = json.has("ts") ? json.get("ts").asLong() : System.currentTimeMillis();
        long now = System.currentTimeMillis();

        if (ts > now + MAX_TIME_SKEW_MS || ts < now - MAX_TIME_SKEW_MS) {
            System.err.println("忽略时间偏差过大的心跳: " + deviceId + " ts=" + ts);
            return;
        }

        Device device = deviceRepository.findByCode(deviceId).orElse(null);
        boolean wasOffline = (device == null) || DeviceStatus.OFFLINE.equals(device.getStatus());

        if (device == null) {
            device = new Device();
            device.setCode(deviceId);
            device.setLocation("未知位置");
            device.setStatus(DeviceStatus.ONLINE);
            device.setCreatedAt(LocalDateTime.now());
        } else {
            if (device.getLastSeen() != null && ts <= device.getLastSeen()) {
                System.out.println("忽略旧心跳: " + deviceId + " ts=" + ts);
                return;
            }
        }

        device.setLastSeen(ts);
        device.setStatus(DeviceStatus.ONLINE);
        deviceRepository.save(device);

        if (wasOffline) {
            alarmService.recoverOfflineAlarms(deviceId);
        }
    }

    private void handleCommandAck(String deviceId, JsonNode json) {
        String commandId = json.has("commandId") ? json.get("commandId").asText() : null;
        if (commandId == null) {
            System.err.println("回执消息缺少 commandId");
            return;
        }

        DeviceCommand command = deviceCommandRepository.findByCommandId(commandId).orElse(null);
        if (command == null) {
            System.err.println("未找到指令记录: " + commandId);
            return;
        }

        String ackStatus = json.has("status") ? json.get("status").asText() : "ACKED";
        switch (ackStatus) {
            case "ACKED":
                command.setStatus(CommandStatus.ACKED);
                break;
            case "SUCCESS":
                command.setStatus(CommandStatus.SUCCESS);
                Device device = deviceRepository.findByCode(deviceId).orElse(null);
                if (device != null) {
                    boolean on = "ON".equalsIgnoreCase(command.getAction());
                    device.setLightOn(on);
                    deviceRepository.save(device);
                }
                break;
            case "FAILED":
                command.setStatus(CommandStatus.FAILED);
                break;
            default:
                command.setStatus(CommandStatus.ACKED);
        }
        command.setUpdatedAt(LocalDateTime.now());
        deviceCommandRepository.save(command);
        System.out.println("指令回执处理完成: " + commandId + " -> " + command.getStatus());
    }

    private String extractDeviceId(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }
}