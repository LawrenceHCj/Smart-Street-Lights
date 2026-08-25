package com.smartlamp.service;

import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AutoControlService {

    @Autowired
    private ConfigService configService;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    @Autowired
    private DeviceRepository deviceRepository;

    /**
     * 根据光照数据自动控制开关灯
     */
    public void handleLightData(String deviceId, double lux) {
        LinkageConfigDTO config = configService.getLinkageConfig();
        if (!config.isEnabled()) {
            return; // 联动功能关闭，不自动控制
        }

        Device device = deviceRepository.findByCode(deviceId).orElse(null);
        if (device == null) {
            return;
        }

        boolean shouldOn = lux < config.getThreshold(); // 光照低于阈值开灯，否则关灯
        boolean currentOn = device.getLightOn() != null && device.getLightOn();

        if (shouldOn != currentOn) {
            // 发送 MQTT 指令
            String payload = "{\"deviceId\":\"" + deviceId + "\",\"on\":" + shouldOn + "}";
            String topic = "device/" + deviceId + "/cmd";
            mqttPublisherService.publish(topic, payload);

            // 更新设备开关状态
            device.setLightOn(shouldOn);
            deviceRepository.save(device);

            System.out.println("自动控制：设备 " + deviceId + " 光照 " + lux + "，执行 " + (shouldOn ? "开灯" : "关灯"));
        }
    }
}