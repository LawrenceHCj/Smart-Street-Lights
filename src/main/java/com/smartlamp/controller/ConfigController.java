package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.dto.SystemConfigDTO;
import com.smartlamp.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
@PreAuthorize("hasRole('admin')")
public class ConfigController {

    @Autowired
    private ConfigService configService;

    // GET /api/config/linkage
    @GetMapping("/linkage")
    public ApiResponse<LinkageConfigDTO> getLinkageConfig() {
        return ApiResponse.success(configService.getLinkageConfig());
    }

    // PUT /api/config/linkage
    @PutMapping("/linkage")
    public ApiResponse<Void> saveLinkageConfig(@RequestBody LinkageConfigDTO config) {
        if (config.getThreshold() < 0 || config.getThreshold() > 10000
                || config.getHysteresis() < 1 || config.getHysteresis() > 1000) {
            return ApiResponse.error(400, "开灯阈值需为 0–10000 Lux，滞回值需为 1–1000 Lux");
        }
        configService.saveLinkageConfig(config);
        return ApiResponse.success(null);
    }
    @GetMapping
    public ApiResponse<SystemConfigDTO> getConfig() {
        return ApiResponse.success(configService.getConfig());
    }

    @PutMapping
    public ApiResponse<SystemConfigDTO> saveConfig(@RequestBody SystemConfigDTO config) {
        if (config.getLuxThreshold() < 10 || config.getLuxThreshold() > 500
                || config.getHysteresis() < 0 || config.getHysteresis() > 200
                || config.getHeartbeatTimeoutMs() < 5000 || config.getHeartbeatTimeoutMs() > 120000) {
            return ApiResponse.error(400, "配置参数超出允许范围");
        }
        configService.saveConfig(config);
        return ApiResponse.success(configService.getConfig());
    }
}
