package com.smartlamp.service;

import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AutoControlService {

    @Autowired
    private ConfigService configService;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceCommandRepository deviceCommandRepository;

    public void handleLightData(String deviceId, double lux) {
        LinkageConfigDTO config = configService.getLinkageConfig();
        if (!config.isEnabled()) {
            return;
        }

        Device device = deviceRepository.findByCode(deviceId).orElse(null);
        if (device == null) {
            return;
        }

        boolean shouldOn = lux < config.getThreshold();
        boolean currentOn = device.getLightOn() != null && device.getLightOn();

        if (shouldOn != currentOn) {
            String action = shouldOn ? "ON" : "OFF";
            String commandId = UUID.randomUUID().toString();

            // 创建指令记录
            DeviceCommand command = new DeviceCommand();
            command.setCommandId(commandId);
            command.setDeviceCode(deviceId);
            command.setAction(action);
            command.setStatus(CommandStatus.DISPATCHED);
            command.setCreatedAt(LocalDateTime.now());
            command.setUpdatedAt(LocalDateTime.now());
            deviceCommandRepository.save(command);

            // 发布指令
            String payload = "{\"deviceId\":\"" + deviceId + "\",\"on\":" + shouldOn + ",\"commandId\":\"" + commandId + "\"}";
            String topic = "device/" + deviceId + "/cmd";
            mqttPublisherService.publish(topic, payload);

            System.out.println("自动控制：设备 " + deviceId + " 光照 " + lux + "，下发 " + action + " 指令，commandId=" + commandId);
        }
    }
}