package com.smartlamp.service;

import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceHealthReport;
import com.smartlamp.repository.DeviceHealthReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DeviceHealthService {

    @Autowired
    private DeviceHealthReportRepository healthReportRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 核心算分逻辑：白盒规则引擎
     */
    public DeviceHealthReport evaluateDeviceHealth(Device device) {
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
            report.setCreatedAt(LocalDateTime.now());

            // 3. 落库保存并返回
            return healthReportRepository.save(report);

        } catch (Exception e) {
            log.error("设备 {} 健康评估执行崩溃，原因: {}", device.getCode(), e.getMessage());
            return null;
        }
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