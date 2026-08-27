package com.smartlamp.service;

import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceHealthReport;
import com.smartlamp.dto.DeviceHealthDTO;
import com.smartlamp.dto.HealthAnomalyDTO;
import com.smartlamp.dto.HealthTelemetryDTO;
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

    /**
     * 核心算分逻辑：白盒规则引擎
     */
    public DeviceHealthReport evaluateDeviceHealth(Device device) {
        if (device == null || !"ONLINE".equalsIgnoreCase(device.getStatus())) {
            if (device != null) log.info("跳过离线设备 {} 的健康评估", device.getCode());
            return null;
        }
        int score = 100;
        ArrayNode anomalyDetails = objectMapper.createArrayNode();

        try {
            // 1. 精准匹配 Device.java 的 latest 系列字段
            Double power = getSafeDouble(device.getLatestPower());
            Double temperature = getSafeDouble(device.getLatestTemperature());
            Double current = getSafeDouble(device.getLatestCurrent());
            Double voltage = getSafeDouble(device.getLatestVoltage());
            String lampStatus = device.getLampStatus();

            // 规则 1：继电器或状态回传异常
            if (power > 0.5 && "OFF".equalsIgnoreCase(lampStatus)) {
                score -= 15;
                addAnomaly(anomalyDetails, "继电器/状态回传异常", "功率不为零(" + power + "W)但系统状态为OFF", 15);
            }

            // 规则 2：过热预警
            if (temperature > 65.0) {
                score -= 10;
                addAnomaly(anomalyDetails, "过热预警", "当前温度(" + temperature + "℃)超过安全阈值", 10);
            }

            // 规则 3：疑似驱动老化
            if (current > 5.0) {
                score -= 10;
                addAnomaly(anomalyDetails, "疑似驱动老化", "工作电流(" + current + "A)异常偏高", 10);
            }

            // 规则 4：供电质量异常 (偏离标准 220V 市电范围)
            if (voltage > 0 && (voltage < 200.0 || voltage > 240.0)) {
                score -= 10;
                addAnomaly(anomalyDetails, "供电质量异常", "当前电压(" + voltage + "V)偏离标准范围", 10);
            }

            score = Math.max(0, score);

            // 2. 组装最终体检报告
            DeviceHealthReport report = new DeviceHealthReport();
            report.setDeviceCode(device.getCode());
            report.setHealthScore(score);
            report.setAnomalyDetails(anomalyDetails.toString());
            report.setTelemetrySnapshot(createTelemetrySnapshot(device).toString());
            report.setCreatedAt(LocalDateTime.now());

            // 3. 落库保存并返回
            return healthReportRepository.save(report);

        } catch (Exception e) {
            log.error("设备 {} 健康评估执行崩溃，原因: {}", device.getCode(), e.getMessage());
            return null;
        }
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
                parseTelemetry(report.getTelemetrySnapshot()),
                parseAnomalies(report.getAnomalyDetails()),
                report.getCreatedAt()
        );
    }

    private ObjectNode createTelemetrySnapshot(Device device) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        putNullable(snapshot, "lux", device.getLatestLux());
        putNullable(snapshot, "temperature", device.getLatestTemperature());
        putNullable(snapshot, "voltage", device.getLatestVoltage());
        putNullable(snapshot, "current", device.getLatestCurrent());
        putNullable(snapshot, "power", device.getLatestPower());
        putNullable(snapshot, "energy", device.getLatestEnergy());
        if (device.getLampStatus() == null) snapshot.putNull("lampStatus");
        else snapshot.put("lampStatus", device.getLampStatus());
        if (device.getLastSeen() == null) snapshot.putNull("collectedAt");
        else snapshot.put("collectedAt", device.getLastSeen());
        return snapshot;
    }

    private HealthTelemetryDTO parseTelemetry(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(snapshot);
            return new HealthTelemetryDTO(
                    nullableDouble(node, "lux"),
                    nullableDouble(node, "temperature"),
                    nullableDouble(node, "voltage"),
                    nullableDouble(node, "current"),
                    nullableDouble(node, "power"),
                    nullableDouble(node, "energy"),
                    nullableText(node, "lampStatus"),
                    nullableLong(node, "collectedAt")
            );
        } catch (Exception e) {
            log.warn("健康报告采集快照解析失败: {}", e.getMessage());
            return null;
        }
    }

    private void putNullable(ObjectNode node, String field, Double value) {
        if (value == null) node.putNull(field);
        else node.put(field, value);
    }

    private Double nullableDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
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

    private Double getSafeDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
