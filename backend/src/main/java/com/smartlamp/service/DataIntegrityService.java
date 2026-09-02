package com.smartlamp.service;

import com.smartlamp.entity.DataIntegrityEntry;
import com.smartlamp.entity.DataIntegrityHead;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.DataIntegrityEntryRepository;
import com.smartlamp.repository.DataIntegrityHeadRepository;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.LightPointRepository;
import com.smartlamp.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 配置零增量的数据完整性服务：从现有 JWT_SECRET 派生独立子密钥，为每台设备维护
 * 一条认证追加链。日志只保存摘要，不复制遥测明文；源数据仍存在时还会复核源摘要。
 */
@Service
public class DataIntegrityService {
    private static final Logger log = LoggerFactory.getLogger(DataIntegrityService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_CONTEXT = "data-integrity-v1";
    private static final String DOMAIN_ENTRY = "DATA_INTEGRITY_ENTRY_V1";
    private static final String DOMAIN_HEAD = "DATA_INTEGRITY_HEAD_V1";
    private static final int PAGE_SIZE = 500;

    public static final String EVENT_TELEMETRY = "TELEMETRY";
    public static final String EVENT_COMMAND_DISPATCHED = "COMMAND_DISPATCHED";
    public static final String EVENT_COMMAND_ACKED = "COMMAND_ACKED";
    public static final String EVENT_COMMAND_SUCCESS = "COMMAND_SUCCESS";
    public static final String EVENT_COMMAND_FAILED = "COMMAND_FAILED";
    public static final String EVENT_COMMAND_TIMEOUT = "COMMAND_TIMEOUT";
    public static final String SOURCE_LIGHT_POINT = "LIGHT_POINT";
    public static final String SOURCE_DEVICE_COMMAND = "DEVICE_COMMAND";

    public static final String ISSUE_HEAD_MAC = "HEAD_MAC_INVALID";
    public static final String ISSUE_SEQUENCE = "SEQUENCE_GAP";
    public static final String ISSUE_PREVIOUS_MAC = "PREVIOUS_MAC_MISMATCH";
    public static final String ISSUE_ENTRY_MAC = "ENTRY_MAC_INVALID";
    public static final String ISSUE_SOURCE = "SOURCE_DIGEST_MISMATCH";
    public static final String ISSUE_TAIL = "CHAIN_TAIL_MISMATCH";
    public static final String ISSUE_KEY_VERSION = "UNSUPPORTED_KEY_VERSION";

    private final DataIntegrityEntryRepository entryRepository;
    private final DataIntegrityHeadRepository headRepository;
    private final LightPointRepository lightPointRepository;
    private final DeviceCommandRepository commandRepository;
    private final ObjectMapper objectMapper;
    private final SecretKeySpec auditKey;

    public DataIntegrityService(DataIntegrityEntryRepository entryRepository,
                                DataIntegrityHeadRepository headRepository,
                                LightPointRepository lightPointRepository,
                                DeviceCommandRepository commandRepository,
                                ObjectMapper objectMapper,
                                JwtUtil jwtUtil) {
        this.entryRepository = entryRepository;
        this.headRepository = headRepository;
        this.lightPointRepository = lightPointRepository;
        this.commandRepository = commandRepository;
        this.objectMapper = objectMapper;
        this.auditKey = new SecretKeySpec(jwtUtil.deriveSubkey(KEY_CONTEXT), HMAC_ALGORITHM);
    }

    @Transactional
    public DataIntegrityEntry appendTelemetry(LightPoint point) {
        String sourceDigest = sha256Hex(telemetrySource(point));
        ObjectNode event = objectMapper.createObjectNode();
        event.put("kind", EVENT_TELEMETRY);
        event.put("sourceDigest", sourceDigest);
        return append(point.getDeviceCode(), EVENT_TELEMETRY, point.getTs(), SOURCE_LIGHT_POINT,
                point.getId(), sha256Hex(write(event)), sourceDigest);
    }

    @Transactional
    public DataIntegrityEntry appendCommand(DeviceCommand command, String eventType) {
        String sourceDigest = sha256Hex(commandSource(command));
        ObjectNode event = objectMapper.createObjectNode();
        event.put("kind", eventType);
        event.put("commandId", command.getCommandId());
        event.put("status", command.getStatus() == null ? null : command.getStatus().name());
        event.put("mode", command.getMode());
        event.put("sourceDigest", sourceDigest);
        return append(command.getDeviceCode(), eventType, System.currentTimeMillis(), SOURCE_DEVICE_COMMAND,
                command.getId(), sha256Hex(write(event)), sourceDigest);
    }

    private DataIntegrityEntry append(String deviceCode, String eventType, long occurredAt,
                                      String sourceType, Long sourceId, String eventDigest, String sourceDigest) {
        if (sourceId == null) throw new IllegalStateException("完整性日志要求业务记录先完成主键分配");
        headRepository.insertHeadIfAbsent(deviceCode);
        DataIntegrityHead head = headRepository.findForUpdate(deviceCode).orElseThrow();
        long sequence = head.getLatestSequence() + 1;
        String previousMac = head.getLatestMac();

        DataIntegrityEntry entry = new DataIntegrityEntry();
        entry.setDeviceCode(deviceCode);
        entry.setSequenceNo(sequence);
        entry.setEventType(eventType);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setOccurredAt(occurredAt);
        entry.setEventDigest(eventDigest);
        entry.setSourceDigest(sourceDigest);
        entry.setPreviousMac(previousMac);
        entry.setKeyVersion(1);
        entry.setChainMac(computeEntryMac(entry));
        entry.setCreatedAt(LocalDateTime.now());
        entryRepository.save(entry);

        head.setLatestSequence(sequence);
        head.setLatestMac(entry.getChainMac());
        head.setKeyVersion(1);
        head.setHeadMac(computeHeadMac(deviceCode, sequence, entry.getChainMac()));
        head.setUpdatedAt(LocalDateTime.now());
        headRepository.save(head);
        return entry;
    }

    /** 完整校验，同时对仍在保留期内的业务源记录做摘要复核。 */
    @Transactional(readOnly = true)
    public VerificationResult verify(String deviceCode) {
        return verifyInternal(deviceCode, true);
    }

    /** 后台自动巡检只做密码学链校验，避免周期任务逐条查询业务表。 */
    @Scheduled(fixedDelayString = "${data-integrity.scan-interval-ms:300000}")
    @Transactional(readOnly = true)
    public void scanAll() {
        for (DataIntegrityHead head : headRepository.findAll()) {
            VerificationResult result = verifyInternal(head.getDeviceCode(), false);
            if (!result.valid()) {
                log.error("设备 {} 数据完整性异常：{}，首个异常序号 {}",
                        result.deviceCode(), result.issue(), result.firstBrokenSequence());
            }
        }
    }

    private VerificationResult verifyInternal(String deviceCode, boolean checkSources) {
        DataIntegrityHead head = headRepository.findById(deviceCode).orElse(null);
        boolean hasEntries = entryRepository.existsByDeviceCode(deviceCode);
        if (head == null) {
            return hasEntries
                    ? invalid(deviceCode, 0, 0, 0, null, ISSUE_TAIL, "存在日志但链头缺失")
                    : new VerificationResult(true, deviceCode, 0, 0, 0, null, null, "暂无完整性日志");
        }
        if (head.getKeyVersion() == null || head.getKeyVersion() != 1) {
            return invalid(deviceCode, 0, 0, head.getLatestSequence(), null, ISSUE_KEY_VERSION,
                    "不支持的链头密钥版本");
        }
        // 链头只会在首条日志的同一事务中创建并完成认证，因此持久化的空链头也属于异常。
        if (head.getLatestSequence() < 1 || !constantTimeEquals(
                computeHeadMac(deviceCode, head.getLatestSequence(), head.getLatestMac()), head.getHeadMac())) {
            return invalid(deviceCode, 0, 0, head.getLatestSequence(), null, ISSUE_HEAD_MAC,
                    "链头认证失败，链尾可能被删除或回退");
        }

        long cursor = 0;
        long expectedSequence = 1;
        String previousMac = "";
        int checkedEntries = 0;
        int checkedSources = 0;
        while (true) {
            List<DataIntegrityEntry> page = entryRepository
                    .findByDeviceCodeAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                            deviceCode, cursor, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) break;
            for (DataIntegrityEntry entry : page) {
                checkedEntries++;
                if (entry.getSequenceNo() != expectedSequence) {
                    return invalid(deviceCode, checkedEntries, checkedSources, head.getLatestSequence(),
                            entry.getSequenceNo(), ISSUE_SEQUENCE, "日志序号不连续");
                }
                if (entry.getKeyVersion() == null || entry.getKeyVersion() != 1) {
                    return invalid(deviceCode, checkedEntries, checkedSources, head.getLatestSequence(),
                            entry.getSequenceNo(), ISSUE_KEY_VERSION, "不支持的完整性密钥版本");
                }
                if (!previousMac.equals(entry.getPreviousMac())) {
                    return invalid(deviceCode, checkedEntries, checkedSources, head.getLatestSequence(),
                            entry.getSequenceNo(), ISSUE_PREVIOUS_MAC, "前序认证码不匹配");
                }
                if (!constantTimeEquals(computeEntryMac(entry), entry.getChainMac())) {
                    return invalid(deviceCode, checkedEntries, checkedSources, head.getLatestSequence(),
                            entry.getSequenceNo(), ISSUE_ENTRY_MAC, "日志内容或认证码被修改");
                }
                if (checkSources) {
                    String currentSourceDigest = currentSourceDigest(entry);
                    if (currentSourceDigest != null) {
                        checkedSources++;
                        if (!constantTimeEquals(currentSourceDigest, entry.getSourceDigest())) {
                            return invalid(deviceCode, checkedEntries, checkedSources, head.getLatestSequence(),
                                    entry.getSequenceNo(), ISSUE_SOURCE, "保留期内的业务源数据被修改");
                        }
                    }
                }
                previousMac = entry.getChainMac();
                cursor = entry.getSequenceNo();
                expectedSequence++;
            }
            if (page.size() < PAGE_SIZE) break;
        }

        long scannedSequence = expectedSequence - 1;
        if (scannedSequence != head.getLatestSequence() || !previousMac.equals(head.getLatestMac())) {
            return invalid(deviceCode, checkedEntries, checkedSources, head.getLatestSequence(),
                    scannedSequence + 1, ISSUE_TAIL, "实际链尾与受认证链头不一致");
        }
        return new VerificationResult(true, deviceCode, checkedEntries, checkedSources,
                head.getLatestSequence(), null, null, "数据完整性校验通过");
    }

    private String currentSourceDigest(DataIntegrityEntry entry) {
        if (SOURCE_LIGHT_POINT.equals(entry.getSourceType())) {
            return lightPointRepository.findById(entry.getSourceId())
                    .map(this::telemetrySource).map(this::sha256Hex).orElse(null);
        }
        if (SOURCE_DEVICE_COMMAND.equals(entry.getSourceType())) {
            return commandRepository.findById(entry.getSourceId())
                    .map(this::commandSource).map(this::sha256Hex).orElse(null);
        }
        return null;
    }

    private String telemetrySource(LightPoint point) {
        ObjectNode node = objectMapper.createObjectNode();
        putText(node, "deviceCode", point.getDeviceCode());
        putLong(node, "ts", point.getTs());
        putNumber(node, "lux", point.getLux());
        putNumber(node, "temperature", point.getTemperature());
        putNumber(node, "voltage", point.getVoltage());
        putNumber(node, "current", point.getCurrent());
        putNumber(node, "power", point.getPower());
        putNumber(node, "energy", point.getEnergy());
        putText(node, "lampStatus", point.getLampStatus());
        putText(node, "rawPayload", point.getRawPayload());
        return write(node);
    }

    private String commandSource(DeviceCommand command) {
        ObjectNode node = objectMapper.createObjectNode();
        putText(node, "commandId", command.getCommandId());
        putText(node, "deviceCode", command.getDeviceCode());
        putText(node, "action", command.getAction());
        putText(node, "mode", command.getMode());
        return write(node);
    }

    private String computeEntryMac(DataIntegrityEntry entry) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("version", 1);
        node.put("deviceCode", entry.getDeviceCode());
        node.put("sequence", entry.getSequenceNo());
        node.put("eventType", entry.getEventType());
        node.put("sourceType", entry.getSourceType());
        node.put("sourceId", entry.getSourceId());
        node.put("occurredAt", entry.getOccurredAt());
        node.put("eventDigest", entry.getEventDigest());
        node.put("sourceDigest", entry.getSourceDigest());
        node.put("previousMac", entry.getPreviousMac());
        return hmacHex(DOMAIN_ENTRY, write(node));
    }

    private String computeHeadMac(String deviceCode, long latestSequence, String latestMac) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("version", 1);
        node.put("deviceCode", deviceCode);
        node.put("latestSequence", latestSequence);
        node.put("latestMac", latestMac);
        return hmacHex(DOMAIN_HEAD, write(node));
    }

    private String hmacHex(String domain, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(auditKey);
            mac.update(domain.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("完整性认证码计算失败", e);
        }
    }

    private String sha256Hex(String value) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("完整性摘要计算失败", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    private void putText(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private void putLong(ObjectNode node, String field, Long value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private void putNumber(ObjectNode node, String field, Double value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private String write(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("完整性载荷序列化失败", e);
        }
    }

    private VerificationResult invalid(String deviceCode, int checkedEntries, int checkedSources,
                                       long latestSequence, Long firstBrokenSequence,
                                       String issue, String message) {
        return new VerificationResult(false, deviceCode, checkedEntries, checkedSources,
                latestSequence, firstBrokenSequence, issue, message);
    }

    public record VerificationResult(boolean valid, String deviceCode, int checkedEntries, int checkedSources,
                                     long latestSequence, Long firstBrokenSequence,
                                     String issue, String message) {
    }
}
