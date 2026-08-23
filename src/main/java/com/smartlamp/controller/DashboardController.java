package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.DashboardOverviewDTO;
import com.smartlamp.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    // GET /api/dashboard/overview
    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewDTO> getOverview() {
        return ApiResponse.success(dashboardService.getOverview());
    }
}