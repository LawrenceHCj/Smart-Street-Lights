package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.LightHistoryDTO;
import com.smartlamp.service.LightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/light")
public class LightController {

    @Autowired
    private LightService lightService;

    // GET /api/light/history?deviceId=SL-001&start=...&end=...
    @GetMapping("/history")
    public ApiResponse<LightHistoryDTO> getHistory(
            @RequestParam String deviceId,
            @RequestParam Long start,
            @RequestParam Long end) {
        LightHistoryDTO history = lightService.getHistory(deviceId, start, end);
        return ApiResponse.success(history);
    }
}