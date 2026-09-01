package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.BrightnessPeriodDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.dto.SystemConfigDTO;
import com.smartlamp.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        String scheduleError = validateBrightnessSchedule(config.getBrightnessPeriods());
        if (scheduleError != null) return ApiResponse.error(400, scheduleError);
        configService.saveLinkageConfig(config);
        return ApiResponse.success(null);
    }

    private String validateBrightnessSchedule(List<BrightnessPeriodDTO> periods) {
        // null 表示旧客户端未修改该配置，后端会保留现值。
        if (periods == null) return null;
        if (periods.isEmpty() || periods.size() > 8) return "分时亮度需配置 1–8 个时段";
        Set<String> starts = new HashSet<>();
        for (BrightnessPeriodDTO period : periods) {
            if (period == null || period.getName() == null || period.getName().isBlank()
                    || period.getName().length() > 20 || period.getName().contains("|")
                    || period.getName().contains(";")) {
                return "时段名称需为 1–20 个字符，且不能包含 | 或 ;";
            }
            try {
                if (period.getStartTime() == null || !period.getStartTime().matches("\\d{2}:\\d{2}")) {
                    return "时段开始时间格式必须为 HH:mm";
                }
                LocalTime.parse(period.getStartTime());
            } catch (DateTimeParseException | NullPointerException error) {
                return "时段开始时间格式必须为 HH:mm";
            }
            if (!starts.add(period.getStartTime())) return "分时亮度的开始时间不能重复";
            if (period.getBrightnessPercent() < 1 || period.getBrightnessPercent() > 100) {
                return "亮度百分比需为 1–100；0% 仍由原有关灯策略负责";
            }
        }
        return null;
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
