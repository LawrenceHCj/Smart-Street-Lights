package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.entity.Alarm;
import com.smartlamp.service.AlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/alarms", "/api/alerts"})
public class AlarmController {

    @Autowired
    private AlarmService alarmService;

    // GET /api/alarms
    @GetMapping
    public ApiResponse<List<Alarm>> listAlarms() {
        return ApiResponse.success(alarmService.getAllAlarms());
    }

    // POST /api/alarms/{id}/ack
    @PostMapping("/{id}/ack")
    public ApiResponse<Void> ackAlarm(@PathVariable Long id) {
        boolean success = alarmService.ackAlarm(id);
        if (!success) {
            return ApiResponse.error(400, "告警不存在");
        }
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/resolve")
    public ApiResponse<Void> resolveAlarm(@PathVariable Long id) {
        return ackAlarm(id);
    }
}
