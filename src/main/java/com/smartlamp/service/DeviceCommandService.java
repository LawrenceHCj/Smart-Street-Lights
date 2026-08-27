package com.smartlamp.service;

import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.entity.enums.DeviceStatus;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DeviceCommandService {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceCommandRepository deviceCommandRepository;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    /**
     * 下发控制指令（开关灯）
     *
     * @param deviceId 设备编号
     * @param action   ON / OFF
     * @return commandId
     * @throws IllegalArgumentException 设备不存在、离线或 action 非法时抛出
     */
    public String dispatchCommand(String deviceId, String action) {
        if (!"ON".equals(action) && !"OFF".equals(action)) {
            throw new IllegalArgumentException("action 必须为 ON 或 OFF");
        }

        Device device = deviceRepository.findByCode(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));

        if (!DeviceStatus.ONLINE.equals(device.getStatus())) {
            throw new IllegalStateException("设备离线，无法下发指令");
        }

        String commandId = UUID.randomUUID().toString();
        boolean on = "ON".equals(action);

        // 保存指令记录
        DeviceCommand command = new DeviceCommand();
        command.setCommandId(commandId);
        command.setDeviceCode(deviceId);
        command.setAction(action);
        command.setStatus(CommandStatus.DISPATCHED);
        command.setCreatedAt(LocalDateTime.now());
        command.setUpdatedAt(LocalDateTime.now());
        deviceCommandRepository.save(command);

        // 发布 MQTT 指令
        String payload = "{\"deviceId\":\"" + deviceId + "\",\"on\":" + on + ",\"commandId\":\"" + commandId + "\"}";
        String topic = "device/" + deviceId + "/cmd";
        mqttPublisherService.publish(topic, payload);

        return commandId;
    }

    /**
     * 查询指令状态
     */
    public DeviceCommand getCommandStatus(String commandId) {
        return deviceCommandRepository.findByCommandId(commandId).orElse(null);
    }
}