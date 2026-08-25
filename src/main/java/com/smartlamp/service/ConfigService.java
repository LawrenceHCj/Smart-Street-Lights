package com.smartlamp.service;

import com.smartlamp.dto.LinkageConfigDTO;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {

    // 内存存储，默认值
    private final LinkageConfigDTO config = new LinkageConfigDTO();

    public ConfigService() {
        config.setEnabled(true);
        config.setThreshold(30);
        config.setHysteresis(5);
        config.setHeartbeatTimeoutMs(90000);
    }

    public LinkageConfigDTO getLinkageConfig() {
        return config;
    }

    public void saveLinkageConfig(LinkageConfigDTO newConfig) {
        config.setEnabled(newConfig.isEnabled());
        config.setThreshold(newConfig.getThreshold());
        if (newConfig.getHysteresis() != 0) {
            config.setHysteresis(newConfig.getHysteresis());
        }
        if (newConfig.getHeartbeatTimeoutMs() != 0) {
            config.setHeartbeatTimeoutMs(newConfig.getHeartbeatTimeoutMs());
        }
    }
}