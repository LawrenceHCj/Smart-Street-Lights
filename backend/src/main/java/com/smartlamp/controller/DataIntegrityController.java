package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.service.DataIntegrityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅提供后端审计接口，不增加前端页面。 */
@RestController
@RequestMapping("/api/integrity")
public class DataIntegrityController {
    private final DataIntegrityService dataIntegrityService;

    public DataIntegrityController(DataIntegrityService dataIntegrityService) {
        this.dataIntegrityService = dataIntegrityService;
    }

    @GetMapping("/{deviceCode}/verify")
    @PreAuthorize("hasAnyRole('admin','municipal')")
    public ApiResponse<DataIntegrityService.VerificationResult> verify(@PathVariable String deviceCode) {
        return ApiResponse.success(dataIntegrityService.verify(deviceCode));
    }
}
