package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.TelemetryDTO;
import com.smartlamp.service.TelemetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {
    @Autowired
    private TelemetryService telemetryService;

    @GetMapping
    public ApiResponse<List<TelemetryDTO>> list(
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "120") int limit) {
        if (limit < 1 || limit > 500) return ApiResponse.error(400, "limit 必须在 1-500 之间");
        return ApiResponse.success(telemetryService.list(deviceId, limit));
    }
}
