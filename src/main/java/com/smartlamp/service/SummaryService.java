package com.smartlamp.service;

import com.smartlamp.dto.SystemConfigDTO;
import com.smartlamp.dto.TelemetryDTO;
import com.smartlamp.entity.Alarm;
import com.smartlamp.repository.AlarmRepository;
import com.smartlamp.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregates persisted operational state for the former Node /api/summary contract. */
@Service
public class SummaryService {
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private AlarmRepository alarmRepository;
    @Autowired private ConfigService configService;
    @Autowired private TelemetryService telemetryService;

    public Map<String, Object> getSummary() {
        var devices = deviceRepository.findAll().stream().map(device -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", device.getCode());
            item.put("name", device.getName());
            item.put("location", device.getLocation());
            item.put("binding", device.getBinding());
            item.put("bound", Boolean.TRUE.equals(device.getBound()));
            item.put("online", "ONLINE".equals(device.getStatus()));
            item.put("lampStatus", device.getLampStatus());
            item.put("lastLux", device.getLatestLux());
            item.put("lastSeenAt", device.getLastSeen());
            return item;
        }).toList();
        var telemetry = telemetryService.list(null, 120);
        var alerts = alarmRepository.findAllByOrderByTsDesc().stream().limit(30).toList();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalDevices", deviceRepository.count());
        metrics.put("onlineDevices", devices.stream().filter(d -> Boolean.TRUE.equals(d.get("online"))).count());
        metrics.put("lampsOn", deviceRepository.countByLampStatus("ON"));
        metrics.put("activeAlerts", alarmRepository.countByStatus("OPEN"));
        metrics.put("latestLux", telemetry.isEmpty() ? null : telemetry.get(0).getLux());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("config", configService.getConfig());
        result.put("metrics", metrics);
        result.put("devices", devices);
        result.put("telemetry", telemetry);
        result.put("alerts", alerts);
        result.put("controlLogs", java.util.List.of());
        return result;
    }
}
