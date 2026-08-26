package com.smartlamp.agent;

import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.service.MqttPublisherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class AgentActionExecutor {

    @Autowired
    private MqttPublisherService mqttPublisherService;

    @Autowired
    private DeviceCommandRepository deviceCommandRepository;

    /**
     * 执行设备控制动作（供智能体调用）
     *
     * @param deviceId 设备编号
     * @param action   动作，支持 "ON" / "OFF"
     * @return 命令ID，后续可通过查询接口获取执行状态
     */
    public String executeControl(String deviceId, String action) {
        if (!"ON".equals(action) && !"OFF".equals(action)) {
            throw new IllegalArgumentException("action 必须为 ON 或 OFF");
        }

        // 生成 commandId
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

        // 发布 MQTT 指令
        boolean on = "ON".equals(action);
        String payload = "{\"deviceId\":\"" + deviceId + "\",\"on\":" + on + ",\"commandId\":\"" + commandId + "\"}";
        String topic = "device/" + deviceId + "/cmd";
        mqttPublisherService.publish(topic, payload);

        return commandId;
    }
}