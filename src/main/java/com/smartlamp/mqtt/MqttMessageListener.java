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

        Device device = deviceRepository.findByCode(deviceId).orElse(null);
        boolean wasOffline = (device == null) || DeviceStatus.OFFLINE.equals(device.getStatus());

        if (device == null) {
            device = new Device();
            device.setCode(deviceId);
            device.setLocation("未知位置");
            device.setStatus(DeviceStatus.ONLINE);
            device.setCreatedAt(LocalDateTime.now());
        }
        device.setLatestLux(lux);
        device.setLastSeen(ts);
        device.setStatus(DeviceStatus.ONLINE);
        deviceRepository.save(device);

        // 如果设备之前是离线，现在上线，恢复离线告警
        if (wasOffline) {
            alarmService.recoverOfflineAlarms(deviceId);
        }

        // 保存光照历史
        LightPoint point = new LightPoint();
        point.setDeviceCode(deviceId);
        point.setLux(lux);
        point.setTs(ts);
        point.setCreatedAt(LocalDateTime.now());
        lightPointRepository.save(point);

        // 自动控制判断
        autoControlService.handleLightData(deviceId, lux);
    }

    private void handleHeartbeat(String deviceId, JsonNode json) {
        long ts = json.has("ts") ? json.get("ts").asLong() : System.currentTimeMillis();

        Device device = deviceRepository.findByCode(deviceId).orElse(null);
        boolean wasOffline = (device == null) || DeviceStatus.OFFLINE.equals(device.getStatus());

        if (device == null) {
            device = new Device();
            device.setCode(deviceId);
            device.setLocation("未知位置");
            device.setStatus(DeviceStatus.ONLINE);
            device.setCreatedAt(LocalDateTime.now());
        }
        device.setLastSeen(ts);
        device.setStatus(DeviceStatus.ONLINE);
        deviceRepository.save(device);

        // 设备恢复在线，恢复离线告警
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
                // 成功后更新设备灯状态
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