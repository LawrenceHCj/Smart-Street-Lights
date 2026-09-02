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
    /** 允许的设备时间戳偏差，超过即视为异常丢弃：前后各 5 分钟。 */
    private static final long MAX_CLOCK_SKEW_MS = 5L * 60 * 1000;

    private final DeviceRepository deviceRepository;
    private final LightPointRepository lightPointRepository;
    private final ObjectMapper objectMapper;
    private final DeviceCommandService commandService;
    private final AlarmService alarmService;
    private final ConfigService configService;
    private final DataIntegrityService dataIntegrityService;

    public MqttIngestionService(DeviceRepository deviceRepository,
                                LightPointRepository lightPointRepository,
                                ObjectMapper objectMapper,
                                DeviceCommandService commandService,
                                AlarmService alarmService,
                                ConfigService configService,
                                DataIntegrityService dataIntegrityService) {
        this.deviceRepository = deviceRepository;
        this.lightPointRepository = lightPointRepository;
        this.objectMapper = objectMapper;
        this.commandService = commandService;
        this.alarmService = alarmService;
        this.configService = configService;
        this.dataIntegrityService = dataIntegrityService;
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
        if (Math.abs(receivedAt - ts) > MAX_CLOCK_SKEW_MS) return;

        Device device = deviceRepository.findByCode(deviceId).orElse(null);
        boolean recoveredFromOffline = device != null && "OFFLINE".equals(device.getStatus());
        if (device == null) device = newDevice(deviceId);

        boolean accepted;
        if ("data".equals(messageType)) {
            accepted = ingestTelemetry(device, json, payload, ts, receivedAt);
        } else {
            // heartbeat：心跳只更新 lastSeen，不更新遥测指标，避免把陈旧遥测误判为新鲜数据。
            // 旧心跳不应让设备重新过期：只有心跳 ts 比当前 lastSeen 更新时才覆盖。
            if (device.getLastSeen() == null || ts >= device.getLastSeen()) {
                device.setLastSeen(ts);
                device.setStatus("ONLINE");
                deviceRepository.save(device);
                accepted = true;
            } else {
                accepted = false;
            }
        }
        if (accepted && recoveredFromOffline) alarmService.recoverOfflineAlarms(deviceId);
    }

    private boolean ingestTelemetry(Device device, JsonNode json, String rawPayload, long ts, long receivedAt) {
        double lux = requiredNumber(json, "lux");
        if (lux < 0) throw new IllegalArgumentException("lux 不能小于 0");

        Double temperature = optionalNumber(json, "temperature");
        Double voltage = optionalNumber(json, "voltage");
        Double current = optionalNumber(json, "current");
        Double power = optionalNumber(json, "power");
        Double energy = optionalNumber(json, "energy");
        String lampStatus = optionalLampStatus(json.get("lampStatus"));
        if (device.getCode().startsWith("SIM-HUXI-")) {
            lampStatus = commandService.resolveReportedLampStatus(device.getCode(), lampStatus);
            if (!"MANUAL".equalsIgnoreCase(device.getControlMode())) {
                lampStatus = configService.resolveSimulatorLampStatus(lux, device.getLampStatus(), lampStatus);
            }
        }

        applyOptionalMetadata(device, json);

        // 2) 防止延迟/重复旧消息覆盖最新快照：仅当采集时间不早于当前 lastTelemetryAt 时才更新设备快照。
        Long prevTelemetryTs = device.getLastTelemetryAt();
        if (prevTelemetryTs != null && ts <= prevTelemetryTs) return false;
        device.setLatestLux(lux);
        device.setLatestTemperature(temperature);
        device.setLatestVoltage(voltage);
        device.setLatestCurrent(current);
        device.setLatestPower(power);
        device.setLatestEnergy(energy);
        if (lampStatus != null) device.setLampStatus(lampStatus);
        device.setLastTelemetryAt(ts);
        device.setLastSeen(device.getLastSeen() == null ? ts : Math.max(device.getLastSeen(), ts));
        device.setStatus("ONLINE");
        deviceRepository.save(device);

        // QoS 1 幂等去重：重复投递可刷新快照，但不会新增历史点。
        if (lightPointRepository.existsByDeviceCodeAndTs(device.getCode(), ts)) return true;

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
        point.setServerReceivedAt(LocalDateTime.now());
        lightPointRepository.save(point);
        // 同事务追加认证摘要；不复制遥测明文，失败时业务数据与完整性日志一起回滚。
        dataIntegrityService.appendTelemetry(point);
        return true;
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

    /**
     * MQTT 自动发现的设备可在首次遥测中携带地图元数据。只补齐尚未维护的字段，
     * 避免后续遥测覆盖管理员在设备管理页手工修正的位置。
     */
    private void applyOptionalMetadata(Device device, JsonNode json) {
        String name = optionalText(json.get("name"), "name", 128);
        String location = optionalText(json.get("location"), "location", 255);
        Double longitude = optionalCoordinate(json.get("longitude"), "longitude", -180, 180);
        Double latitude = optionalCoordinate(json.get("latitude"), "latitude", -90, 90);

        if (name != null && (device.getName() == null || device.getName().isBlank()
                || device.getName().equals(device.getCode()))) {
            device.setName(name);
        }
        if (location != null && (device.getLocation() == null || device.getLocation().isBlank()
                || "未知位置".equals(device.getLocation()))) {
            device.setLocation(location);
        }
        if (longitude != null && device.getLongitude() == null) device.setLongitude(longitude);
        if (latitude != null && device.getLatitude() == null) device.setLatitude(latitude);
    }

    private String optionalText(JsonNode node, String field, int maxLength) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException(field + " 必须是非空字符串");
        }
        String value = node.asText().trim();
        if (value.length() > maxLength) throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength);
        return value;
    }

    private Double optionalCoordinate(JsonNode node, String field, double min, double max) {
        if (node == null || node.isNull()) return null;
        double value;
        if (node.isNumber()) {
            value = node.asDouble();
        } else if (node.isTextual()) {
            try {
                value = Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(field + " 必须是数值");
            }
        } else {
            throw new IllegalArgumentException(field + " 必须是数值");
        }
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(field + " 超出有效范围");
        }
        return value;
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
