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

    /** 遥测数据超过该时长（毫秒）即视为陈旧，不应再据此评分。默认 10 分钟。 */
    private static final long STALE_TELEMETRY_MS = 10L * 60 * 1000;

    @Autowired
    private DeviceHealthReportRepository healthReportRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 核心算分逻辑：白盒规则引擎
     *
     * 业务约定：
     * - 设备不存在：返回 null（由调用方决定如何回复客户端，避免在这里抛 500）。
     * - 设备没有任何遥测数据：返回 null，不写入 100 分报告。
     * - 遥测数据陈旧（超过 STALE_TELEMETRY_MS）：返回 null，不写入满分报告。
     * - 数据库或序列化异常：原样向上抛出，由 GlobalExceptionHandler 统一返回 500，
     *   不再吞掉异常伪装成成功。
     */
    public DeviceHealthReport evaluateDeviceHealth(Device device) {
        if (device == null || !"ONLINE".equalsIgnoreCase(device.getStatus())) return null;

        ArrayNode anomalyDetails = objectMapper.createArrayNode();

        // 1) 取值并判断数据可用性
        Double power = getNullableDouble(device.getLatestPower());
        Double temperature = getNullableDouble(device.getLatestTemperature());
        Double current = getNullableDouble(device.getLatestCurrent());
        Double voltage = getNullableDouble(device.getLatestVoltage());
        String lampStatus = device.getLampStatus();

        // 1.1 遥测是否完全缺失？
        boolean allTelemetryMissing =
                power == null && temperature == null && current == null && voltage == null;
        if (allTelemetryMissing) {
            // 仅心跳、无任何电气数据：明确"数据不足"，不写满分报告。
            log.debug("设备 {} 无任何遥测数据，跳过评分（不写入满分报告）", device.getCode());
            return null;
        }

        // 1.2 遥测是否陈旧？陈旧数据不应被当作刚采集，更不应据此得出满分。
        long now = System.currentTimeMillis();
        Long lastTelemetryAt = device.getLastTelemetryAt();
        if (lastTelemetryAt != null && (now - lastTelemetryAt) > STALE_TELEMETRY_MS) {
            log.debug("设备 {} 遥测已陈旧（lastTelemetryAt={}），跳过评分", device.getCode(), lastTelemetryAt);
            return null;
        }

        // 2) 评分（不存在的指标不扣分，也不当作 0 处理）
        int score = 100;

        // 规则 1：继电器或状态回传异常（需要 power 与 lampStatus 都存在）
        if (power != null && power > 0.5 && "OFF".equalsIgnoreCase(lampStatus)) {
            score -= 15;
            addAnomaly(anomalyDetails, "继电器/状态回传异常",
                    "功率不为零(" + power + "W)但系统状态为OFF", 15);
        }

        // 规则 2：过热预警
        if (temperature != null && temperature > 65.0) {
            score -= 10;
            addAnomaly(anomalyDetails, "过热预警",
                    "当前温度(" + temperature + "℃)超过安全阈值", 10);
        }

        // 规则 3：疑似驱动老化
        if (current != null && current > 5.0) {
            score -= 10;
            addAnomaly(anomalyDetails, "疑似驱动老化",
                    "工作电流(" + current + "A)异常偏高", 10);
        }

        // 规则 4：供电质量异常（偏离标准 220V 市电范围）
        if (voltage != null && voltage > 0 && (voltage < 200.0 || voltage > 240.0)) {
            score -= 10;
            addAnomaly(anomalyDetails, "供电质量异常",
                    "当前电压(" + voltage + "V)偏离标准范围", 10);
        }

        score = Math.max(0, score);

        // 3) 组装并落库（任何异常向上抛，交给 GlobalExceptionHandler）
        DeviceHealthReport report = new DeviceHealthReport();
        report.setDeviceCode(device.getCode());
        report.setHealthScore(score);
        report.setAnomalyDetails(anomalyDetails.toString());
        report.setTelemetrySnapshot(createTelemetrySnapshot(device).toString());
        report.setCreatedAt(LocalDateTime.now());
        return healthReportRepository.save(report);
    }

    public DeviceHealthDTO evaluateDeviceHealth(String deviceCode) {
        Device device = deviceRepository.findByCode(deviceCode).orElse(null);
        if (device == null) return null;
        return toDTO(evaluateDeviceHealth(device));
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
                    nullableDouble(node, "lux"), nullableDouble(node, "temperature"),
                    nullableDouble(node, "voltage"), nullableDouble(node, "current"),
                    nullableDouble(node, "power"), nullableDouble(node, "energy"),
                    nullableText(node, "lampStatus"), nullableLong(node, "collectedAt"));
        } catch (Exception e) {
            log.warn("健康报告采集快照解析失败: {}", e.getMessage());
            return null;
        }
    }

    private void putNullable(ObjectNode node, String field, Double value) {
        if (value == null) node.putNull(field); else node.put(field, value);
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
            // 解析历史报告的异常详情失败，仅记录，不应让整个查询返回 500。
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

    /** 缺失/null 不再被转换为 0，而是返回 null，避免把"无数据"误判为"零值正常"。 */
    private Double getNullableDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        try {
            double d = Double.parseDouble(value.toString());
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
