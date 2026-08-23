package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.dto.SystemConfigDTO;
import com.smartlamp.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
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
        // 简单校验阈值范围 0-500（前端已约束，这里再防一手）
        if (config.getThreshold() < 0 || config.getThreshold() > 500) {
            return ApiResponse.error(400, "阈值必须在 0-500 之间");
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
