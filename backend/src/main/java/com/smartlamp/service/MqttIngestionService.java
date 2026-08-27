package com.smartlamp.service;

import com.smartlamp.entity.Device;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.LightPointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MqttIngestionService {
    private static final Pattern TOPIC_PATTERN = Pattern.compile("^device/([^/]+)/(data|heartbeat|cmd_ack)$");
    private static final int MAX_PAYLOAD_LENGTH = 65_535;
    /** 允许的设备时间戳偏差，超过即视为异常丢弃：前后各 24 小时。 */
    private static final long MAX_CLOCK_SKEW_MS = 24L * 60 * 60 * 1000;

    private final DeviceRepository deviceRepository;
    private final LightPointRepository lightPointRepository;
    private final ObjectMapper objectMapper;
    private final DeviceCommandService commandService;
    private final AlarmService alarmService;

    public MqttIngestionService(DeviceRepository deviceRepository,
                                LightPointRepository lightPointRepository,
                                ObjectMapper objectMapper,
                                DeviceCommandService commandService,
                                AlarmService alarmService) {
        this.deviceRepository = deviceRepository;
        this.lightPointRepository = lightPointRepository;
        this.objectMapper = objectMapper;
        this.commandService = commandService;
        this.alarmService = alarmService;
    }

    @Transactional
    public void ingest(String topic, String payload) throws Exception {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("MQTT topic 不能为空");
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("MQTT payload 不能为空");
        if (payload.length() > MAX_PAYLOAD_LENGTH) throw new IllegalArgumentException("MQTT payload 超过 65535 字符");

        Matcher topicMatcher = TOPIC_PATTERN.matcher(topic);
        if (!topicMatcher.matches()) throw new IllegalArgumentException("不支持的 MQTT topic: " + topic);

        String topicDeviceId = topicMatcher.group(1).trim();
        if (topicDeviceId.isEmpty() || topicDeviceId.length() > 128) {
            throw new IllegalArgumentException("deviceId 长度必须在 1-128 之间");
        }

        JsonNode json = objectMapper.readTree(payload);
        if (json == null || !json.isObject()) throw new IllegalArgumentException("MQTT payload 必须是 JSON 对象");

        String deviceId = topicDeviceId;
        JsonNode payloadDeviceId = json.get("deviceId");
        if (payloadDeviceId != null && !payloadDeviceId.isNull()) {
            if (!payloadDeviceId.isTextual() || payloadDeviceId.asText().isBlank()) {
                throw new IllegalArgumentException("payload.deviceId 必须是非空字符串");
            }
            deviceId = payloadDeviceId.asText().trim();
            if (!topicDeviceId.equals(deviceId)) {
                throw new IllegalArgumentException("topic 与 payload 的 deviceId 不一致");
            }
        }

        String messageType = topicMatcher.group(2);
        if ("cmd_ack".equals(messageType)) {
            commandService.acknowledge(deviceId, json);
            return;
        }

        long ts = readTimestamp(json.get("ts"));
        long receivedAt = System.currentTimeMillis();
        validateClockSkew(ts, receivedAt);

        Device device = deviceRepository.findByCode(deviceId).orElse(null);
        boolean recoveredFromOffline = device != null && "OFFLINE".equals(device.getStatus());
        if (device == null) device = newDevice(deviceId);

        if ("data".equals(messageType)) {
            ingestTelemetry(device, json, payload, ts, receivedAt);
        } else {
            // heartbeat：心跳只更新 lastSeen，不更新遥测指标，避免把陈旧遥测误判为新鲜数据。
            // 旧心跳不应让设备重新过期：只有心跳 ts 比当前 lastSeen 更新时才覆盖。
            if (device.getLastSeen() == null || ts >= device.getLastSeen()) {
                device.setLastSeen(ts);
                device.setStatus("ONLINE");
                deviceRepository.save(device);
            }
        }
        if (recoveredFromOffline) alarmService.recoverOfflineAlarms(deviceId);
    }

    private void ingestTelemetry(Device device, JsonNode json, String rawPayload, long ts, long receivedAt) {
        // 1) 幂等去重：以 (deviceCode, ts) 为唯一键，重复 QoS 投递直接丢弃，避免覆盖当前快照。
        if (lightPointRepository.existsByDeviceCodeAndTs(device.getCode(), ts)) {
            return;
        }

        double lux = requiredNumber(json, "lux");
        if (lux < 0) throw new IllegalArgumentException("lux 不能小于 0");

        Double temperature = optionalNumber(json, "temperature");
        Double voltage = optionalNumber(json, "voltage");
        Double current = optionalNumber(json, "current");
        Double power = optionalNumber(json, "power");
        Double energy = optionalNumber(json, "energy");
        String lampStatus = optionalLampStatus(json.get("lampStatus"));

        // 2) 防止延迟/重复旧消息覆盖最新快照：仅当采集时间不早于当前 lastTelemetryAt 时才更新设备快照。
        Long prevTelemetryTs = device.getLastTelemetryAt();
        if (prevTelemetryTs == null || ts >= prevTelemetryTs) {
            device.setLatestLux(lux);
            device.setLatestTemperature(temperature);
            device.setLatestVoltage(voltage);
            device.setLatestCurrent(current);
            device.setLatestPower(power);
            device.setLatestEnergy(energy);
            if (lampStatus != null) device.setLampStatus(lampStatus);
            device.setLastTelemetryAt(ts);
            // 数据消息本身代表设备在线；只有更新快照时才推进 lastSeen，避免旧消息拉长在线窗口。
            device.setLastSeen(ts);
            device.setStatus("ONLINE");
            deviceRepository.save(device);
        }

        // 3) 落库历史点（receivedAt 用于区分采集时间与服务接收时间）。
        LightPoint point = new LightPoint();
        point.setDeviceCode(device.getCode());
        point.setLux(lux);
        point.setTemperature(temperature);
        point.setVoltage(voltage);
        point.setCurrent(current);
        point.setPower(power);
        point.setEnergy(energy);
        point.setLampStatus(lampStatus);
        point.setTs(ts);
        point.setRawPayload(rawPayload);
        point.setCreatedAt(LocalDateTime.now());
        lightPointRepository.save(point);
    }

    private void validateClockSkew(long ts, long receivedAt) {
        if (Math.abs(receivedAt - ts) > MAX_CLOCK_SKEW_MS) {
            throw new IllegalArgumentException(
                    "设备时间戳与服务器时间偏差超过 " + (MAX_CLOCK_SKEW_MS / 3600000) + " 小时，疑似伪造或时钟漂移，拒绝该消息");
        }
    }

    private Device newDevice(String deviceId) {
        Device device = new Device();
        device.setCode(deviceId);
        device.setName(deviceId);
        device.setLocation("未知位置");
        device.setStatus("ONLINE");
        device.setCreatedAt(LocalDateTime.now());
        return device;
    }

    private long readTimestamp(JsonNode node) {
        if (node == null || node.isNull()) return System.currentTimeMillis();
        if (!node.isIntegralNumber()) throw new IllegalArgumentException("ts 必须是毫秒整数时间戳");
        long ts = node.asLong();
        if (ts <= 0) throw new IllegalArgumentException("ts 必须大于 0");
        return ts;
    }

    private double requiredNumber(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) throw new IllegalArgumentException(field + " 为必填数值");
        return finiteNumber(node, field);
    }

    private Double optionalNumber(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || node.isNull() ? null : finiteNumber(node, field);
    }

    private double finiteNumber(JsonNode node, String field) {
        if (!node.isNumber()) throw new IllegalArgumentException(field + " 必须是数值");
        double value = node.asDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(field + " 必须是有限数值");
        return value;
    }

    private String optionalLampStatus(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw new IllegalArgumentException("lampStatus 必须是 ON 或 OFF");
        String status = node.asText().trim().toUpperCase();
        if (!"ON".equals(status) && !"OFF".equals(status)) {
            throw new IllegalArgumentException("lampStatus 必须是 ON 或 OFF");
        }
        return status;
    }
}
