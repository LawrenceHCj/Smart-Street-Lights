package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.LightHistoryDTO;
import com.smartlamp.service.LightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class LightController {

    @Autowired
    private LightService lightService;

    // 原有历史接口
    @GetMapping("/api/light/history")
    public ApiResponse<LightHistoryDTO> getHistory(
            @RequestParam String deviceId,
            @RequestParam Long start,
            @RequestParam Long end) {
        LightHistoryDTO history = lightService.getHistory(deviceId, start, end);
        return ApiResponse.success(history);
    }

    // 新增遥测接口
    @GetMapping("/api/telemetry")
    public ApiResponse<LightHistoryDTO> getTelemetry(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "10") int limit) {
        LightHistoryDTO data = lightService.getRecentTelemetry(deviceId, limit);
        return ApiResponse.success(data);
    }
}