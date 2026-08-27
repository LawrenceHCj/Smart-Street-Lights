package com.smartlamp.service;

import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.dto.SystemConfigDTO;
import com.smartlamp.entity.SystemConfig;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {

    @Autowired
    private SystemConfigRepository repository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    private SystemConfig config() {
        return repository.findById(1L).orElseGet(() -> repository.save(new SystemConfig()));
    }

    public LinkageConfigDTO getLinkageConfig() {
        SystemConfig config = config();
        LinkageConfigDTO dto = new LinkageConfigDTO();
        dto.setEnabled(config.isAutoControl());
        dto.setThreshold(config.getLuxThreshold());
        dto.setHysteresis(config.getHysteresis());
        return dto;
    }

    public void saveLinkageConfig(LinkageConfigDTO newConfig) {
        SystemConfig config = config();
        config.setAutoControl(newConfig.isEnabled());
        config.setLuxThreshold(newConfig.getThreshold());
        config.setHysteresis(newConfig.getHysteresis());
        repository.save(config);
        publishLinkageConfig(newConfig);
    }

    private void publishLinkageConfig(LinkageConfigDTO config) {
        int offLux = config.getThreshold() + config.getHysteresis();
        for (Device device : deviceRepository.findAll()) {
            if (!Boolean.TRUE.equals(device.getBound())) continue;
            String deviceId = device.getCode();
            String payload = "{\"deviceId\":\"" + deviceId
                    + "\",\"auto\":" + config.isEnabled()
                    + ",\"onLux\":" + config.getThreshold()
                    + ",\"offLux\":" + offLux + "}";
            mqttPublisherService.publish("device/" + deviceId + "/cmd", payload);
        }
    }

    public SystemConfigDTO getConfig() {
        SystemConfig config = config();
        SystemConfigDTO dto = new SystemConfigDTO();
        dto.setAutoControl(config.isAutoControl());
        dto.setLuxThreshold(config.getLuxThreshold());
        dto.setHysteresis(config.getHysteresis());
        dto.setHeartbeatTimeoutMs(config.getHeartbeatTimeoutMs());
        return dto;
    }

    public void saveConfig(SystemConfigDTO dto) {
        SystemConfig config = config();
        config.setAutoControl(dto.isAutoControl());
        config.setLuxThreshold(dto.getLuxThreshold());
        config.setHysteresis(dto.getHysteresis());
        config.setHeartbeatTimeoutMs(dto.getHeartbeatTimeoutMs());
        repository.save(config);

        LinkageConfigDTO linkage = new LinkageConfigDTO();
        linkage.setEnabled(dto.isAutoControl());
        linkage.setThreshold(dto.getLuxThreshold());
        linkage.setHysteresis(dto.getHysteresis());
        publishLinkageConfig(linkage);
    }
}
