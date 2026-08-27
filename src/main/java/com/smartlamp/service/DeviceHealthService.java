package com.smartlamp.service;

import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceHealthReport;
import com.smartlamp.dto.DeviceHealthDTO;
import com.smartlamp.dto.HealthAnomalyDTO;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.DeviceHealthReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DeviceHealthService {

    @Autowired
    private DeviceHealthReportRepository healthReportRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeviceHealthReport evaluateDeviceHealth(Device device) {
        int score = 100;
        ArrayNode anomalyDetails = objectMapper.createArrayNode();

        // 数据有效性检查（直接返回 null，代表无法评分，由控制器转为业务错误）
        if (device.getLastTelemetryAt() == null) {
            log.warn("设备 {} 无遥测数据，无法评分", device.getCode());
            return null;
        }
        long now = System.currentTimeMillis();
        long maxDataAgeMs = 5 * 60 * 1000;
        if (now - device.getLastTelemetryAt() > maxDataAgeMs) {
            log.warn("设备 {} 遥测数据已过期，无法评分", device.getCode());
            return null;
        }

        if (device.getLatestTemperature() == null && device.getLatestCurrent() == null
                && device.getLatestVoltage() == null && device.getLatestPower() == null) {
            log.warn("设备 {} 缺少关键遥测指标，无法评分", device.getCode());
            return null;
        }

        // 评分子逻辑不再捕获异常，让数据库或其他异常自然抛出
        Double power = device.getLatestPower();
        Double temperature = device.getLatestTemperature();
        Double current = device.getLatestCurrent();
        Double voltage = device.getLatestVoltage();
        String lampStatus = device.getLampStatus();

        if (power != null && power > 0.5 && "OFF".equalsIgnoreCase(lampStatus)) {
            score -= 15;
            addAnomaly(anomalyDetails, "继电器/状态回传异常", "功率不为零(" + power + "W)但系统状态为OFF", 15);
        }

        if (temperature != null && temperature > 65.0) {
            score -= 10;
            addAnomaly(anomalyDetails, "过热预警", "当前温度(" + temperature + "℃)超过安全阈值", 10);
        }

        if (current != null && current > 5.0) {
            score -= 10;
            addAnomaly(anomalyDetails, "疑似驱动老化", "工作电流(" + current + "A)异常偏高", 10);
        }

        if (voltage != null && voltage > 0 && (voltage < 200.0 || voltage > 240.0)) {
            score -= 10;
            addAnomaly(anomalyDetails, "供电质量异常", "当前电压(" + voltage + "V)偏离标准范围", 10);
        }

        score = Math.max(0, score);

        DeviceHealthReport report = new DeviceHealthReport();
        report.setDeviceCode(device.getCode());
        report.setHealthScore(score);
        report.setAnomalyDetails(anomalyDetails.toString());
        report.setCreatedAt(LocalDateTime.now());

        return healthReportRepository.save(report);
    }

    public DeviceHealthDTO evaluateDeviceHealth(String deviceCode) {
        Device device = deviceRepository.findByCode(deviceCode).orElse(null);
        return device == null ? null : toDTO(evaluateDeviceHealth(device));
    }

    public List<DeviceHealthDTO> getLatestReports() {
        return healthReportRepository.findLatestForAllDevices().stream()
                .map(this::toDTO)
                .toList();
    }

    public List<DeviceHealthDTO> getHistory(String deviceCode) {
        return healthReportRepository.findTop30ByDeviceCodeOrderByCreatedAtDesc(deviceCode).stream()
                .map(this::toDTO)
                .toList();
    }

    public DeviceHealthDTO toDTO(DeviceHealthReport report) {
        if (report == null) return null;
        return new DeviceHealthDTO(
                report.getId(),
                report.getDeviceCode(),
                report.getHealthScore(),
                parseAnomalies(report.getAnomalyDetails()),
                report.getCreatedAt()
        );
    }

    private List<HealthAnomalyDTO> parseAnomalies(String details) {
        List<HealthAnomalyDTO> anomalies = new ArrayList<>();
        if (details == null || details.isBlank()) return anomalies;
        try {
            JsonNode root = objectMapper.readTree(details);
            if (!root.isArray()) return anomalies;
            for (JsonNode node : root) {
                anomalies.add(new HealthAnomalyDTO(
                        node.path("issue").asText("未知异常"),
                        node.path("reason").asText(""),
                        node.path("deduct").asInt(0)
                ));
            }
        } catch (Exception e) {
            log.warn("健康报告异常详情解析失败: {}", e.getMessage());
        }
        return anomalies;
    }

    private void addAnomaly(ArrayNode array, String issue, String reason, int deduct) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("issue", issue);
        node.put("reason", reason);
        node.put("deduct", deduct);
        array.add(node);
    }
}