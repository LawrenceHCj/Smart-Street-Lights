package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.LinkageConfigDTO;
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
        if (config.getThreshold() < 0 || config.getThreshold() > 500) {
            return ApiResponse.error(400, "阈值必须在 0-500 之间");
        }
        configService.saveLinkageConfig(config);
        return ApiResponse.success(null);
    }

    // GET /api/config —— 系统配置
    @GetMapping
    public ApiResponse<LinkageConfigDTO> getConfig() {
        return ApiResponse.success(configService.getLinkageConfig());
    }

    // PUT /api/config —— 系统配置
    @PutMapping
    public ApiResponse<Void> saveConfig(@RequestBody LinkageConfigDTO config) {
        if (config.getThreshold() < 0 || config.getThreshold() > 500) {
            return ApiResponse.error(400, "阈值必须在 0-500 之间");
        }
        configService.saveLinkageConfig(config);
        return ApiResponse.success(null);
    }
}