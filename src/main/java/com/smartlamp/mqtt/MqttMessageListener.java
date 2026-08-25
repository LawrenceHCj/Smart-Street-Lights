package com.smartlamp.mqtt;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.LightPointRepository;
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
    private AutoControlService autoControlService;   // 新增注入

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
                double lux = json.get("lux").asDouble();
                long ts = json.has("ts") ? json.get("ts").asLong() : System.currentTimeMillis();

                // 更新设备表
                Device device = deviceRepository.findByCode(deviceId).orElse(null);
                if (device == null) {
                    device = new Device();
                    device.setCode(deviceId);
                    device.setLocation("未知位置");
                    device.setStatus("ONLINE");
                    device.setCreatedAt(LocalDateTime.now());
                }
                device.setLatestLux(lux);
                device.setLastSeen(ts);
                device.setStatus("ONLINE");
                deviceRepository.save(device);

                // 保存光照历史
                LightPoint point = new LightPoint();
                point.setDeviceCode(deviceId);
                point.setLux(lux);
                point.setTs(ts);
                point.setCreatedAt(LocalDateTime.now());
                lightPointRepository.save(point);

                // 新增：自动控制判断
                autoControlService.handleLightData(deviceId, lux);

            } else if (topic.endsWith("/heartbeat")) {
                long ts = json.has("ts") ? json.get("ts").asLong() : System.currentTimeMillis();
                Device device = deviceRepository.findByCode(deviceId).orElse(null);
                if (device == null) {
                    device = new Device();
                    device.setCode(deviceId);
                    device.setLocation("未知位置");
                    device.setStatus("ONLINE");
                    device.setCreatedAt(LocalDateTime.now());
                }
                device.setLastSeen(ts);
                device.setStatus("ONLINE");
                deviceRepository.save(device);
            }
        } catch (Exception e) {
            System.err.println("处理 MQTT 消息失败: " + e.getMessage());
        }
    }

    private String extractDeviceId(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }
}