package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.EnergySavingsDTO;
import com.smartlamp.service.EnergySavingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/energy-savings")
public class EnergySavingsController {
    private final EnergySavingsService energySavingsService;

    public EnergySavingsController(EnergySavingsService energySavingsService) {
        this.energySavingsService = energySavingsService;
    }

    @GetMapping
    public ApiResponse<EnergySavingsDTO> getEnergySavings(@RequestParam(defaultValue = "7") int days) {
        if (days < 1 || days > 30) return ApiResponse.error(400, "days 必须在 1–30 之间");
        return ApiResponse.success(energySavingsService.analyze(days));
    }
}
