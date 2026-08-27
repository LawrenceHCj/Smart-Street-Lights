package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {
    private final SummaryService summaryService;
    public SummaryController(SummaryService summaryService) { this.summaryService = summaryService; }

    @GetMapping
    public ApiResponse<Map<String, Object>> getSummary() {
        return ApiResponse.success(summaryService.getSummary());
    }
}
