package com.smartlamp.service;

import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.EvidenceAnchor;
import com.smartlamp.entity.EvidenceChainEntry;
import com.smartlamp.entity.EvidenceChainHead;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.EvidenceAnchorRepository;
import com.smartlamp.repository.EvidenceChainEntryRepository;
import com.smartlamp.repository.EvidenceChainHeadRepository;
import com.smartlamp.repository.LightPointRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 遥测防篡改证据链（最终安全模型）：
 *  1. SHA-256 哈希链：每条事件按 seq 串联，负责顺序、指针、内容完整性；
 *  2. 每条 Entry 独立 HMAC：对 (domain + keyId + deviceCode + seq + entryHash) 做应用密钥认证，
 *     使仅取得数据库写权限但未取得应用密钥的攻击者即使重算普通哈希链也无法伪造；
 *  3. ChainHead HMAC：防止未锚定尾段删除后回退 head.latestSeq/latestHash；
 *  4. Periodic Anchor：阶段性审计检查点，展示锚定覆盖范围。
 *
 * 校验维度：Head MAC → Entry MAC + 哈希链(seq 连续 + prevHash 指针 + hash) → 链头一致 → 业务一致 → 锚点。
 * 威胁模型边界：持有应用 HMAC 密钥的主体仍可重新签名；"删除整个存储/恢复历史快照"需外部审计、
 * 数据库权限隔离或备份审计才能识别，不声称绝对不可回滚（见 P0-28/P0-12）。
 */
@Slf4j
@Service
public class EvidenceChainService {

    public static final String EVENT_TELEMETRY = "TELEMETRY";
    public static final String EVENT_COMMAND = "COMMAND";
    public static final String EVENT_ACK = "ACK";
    public static final String SOURCE_LIGHT_POINT = "LIGHT_POINT";
    public static final String SOURCE_DEVICE_COMMAND = "DEVICE_COMMAND";

    /** 断点类型 */
    public static final String BREAK_CHAIN = "CHAIN_BROKEN";
    public static final String BREAK_SEQUENCE_GAP = "CHAIN_SEQUENCE_GAP";
    public static final String BREAK_UNSUPPORTED_HASH_VERSION = "UNSUPPORTED_HASH_VERSION";
    public static final String BREAK_UNSUPPORTED_PAYLOAD_VERSION = "UNSUPPORTED_PAYLOAD_VERSION";
    public static final String BREAK_ENTRY_MAC = "ENTRY_MAC_INVALID";
    public static final String BREAK_UNKNOWN_MAC_KEY = "UNKNOWN_MAC_KEY";
    public static final String BREAK_UNSUPPORTED_MAC_VERSION = "UNSUPPORTED_MAC_VERSION";
    public static final String BREAK_CHAIN_HEAD_MAC_INVALID = "CHAIN_HEAD_MAC_INVALID";
    public static final String BREAK_TRUNCATED = "CHAIN_TRUNCATED";
    public static final String BREAK_HEAD_ROLLBACK = "CHAIN_HEAD_ROLLBACK";
    public static final String BREAK_HEAD_MISMATCH = "CHAIN_HEAD_MISMATCH";
    public static final String BREAK_HEAD_ANCHOR_INCONSISTENT = "CHAIN_HEAD_ANCHOR_INCONSISTENT";
    public static final String BREAK_SOURCE_MISSING = "SOURCE_MISSING";
    public static final String BREAK_SOURCE_MISMATCH = "SOURCE_MISMATCH";

    /** 锚点状态 */
    public static final String ANCHOR_DISABLED = "DISABLED";
    public static final String ANCHOR_NONE = "NONE";
    public static final String ANCHOR_VALID = "VALID";
    public static final String ANCHOR_SIGNATURE_INVALID = "SIGNATURE_INVALID";
    public static final String ANCHOR_CHAIN_MISMATCH = "CHAIN_MISMATCH";
    public static final String ANCHOR_ENTRY_MISSING = "ENTRY_MISSING";

    /** 分量状态 */
    public static final String S_VALID = "VALID";
    public static final String S_INVALID = "INVALID";
    public static final String S_DISABLED = "DISABLED";
    public static final String S_NOT_CHECKED = "NOT_CHECKED";

    /** 整体结论 */
    public static final String STATUS_VALID_ANCHORED = "VALID_ANCHORED";
    public static final String STATUS_VALID_PARTIALLY_ANCHORED = "VALID_PARTIALLY_ANCHORED";
    public static final String STATUS_VALID_UNANCHORED = "VALID_UNANCHORED";
    public static final String STATUS_INVALID_CHAIN = "INVALID_CHAIN";
    public static final String STATUS_INVALID_MAC = "INVALID_MAC";
    public static final String STATUS_INVALID_SOURCE = "INVALID_SOURCE";
    public static final String STATUS_INVALID_ANCHOR = "INVALID_ANCHOR";

    /** 链头状态 */
    public static final String HEAD_STATE_VALID = "VALID";
    public static final String HEAD_STATE_INVALID = "INVALID";
    public static final String HEAD_STATE_NONE = "NONE";
    /** 元数据一致性状态 */
    public static final String META_NORMAL = "NORMAL";
    public static final String META_INCONSISTENT = "INCONSISTENT";
    public static final String META_ISSUE_HEAD_MISSING = "HEAD_MISSING";
    public static final String META_ISSUE_HEAD_MAC_INVALID = "HEAD_MAC_INVALID";
    public static final String META_ISSUE_ANCHOR_AHEAD_OF_HEAD = "ANCHOR_AHEAD_OF_HEAD";

    private static final String HMAC_ALG = "HmacSHA256";
    private static final int PAGE_SIZE = 500;
    /** 当前 append 使用的版本号（未来升级时递增） */
    private static final int HASH_VERSION = 1;
    private static final int PAYLOAD_VERSION = 1;
    private static final int MAC_VERSION = 1;
    /** 历史 null 字段固定 fallback 的 legacy 版本号（不可变，用于旧数据） */
    private static final int LEGACY_HASH_VERSION = 1;
    private static final int LEGACY_PAYLOAD_VERSION = 1;
    private static final int LEGACY_MAC_VERSION = 1;

    private static final String DOMAIN_ENTRY_MAC = "ENTRY_MAC";
    private static final String DOMAIN_CHAIN_HEAD = "CHAIN_HEAD";
    private static final String DOMAIN_ANCHOR = "ANCHOR";

    @Value("${evidence.chain.enabled:true}")
    private boolean chainEnabled;

    @Value("${evidence.chain.hmac.current-key-id:v1}")
    private String currentKeyId;

    @Value("${evidence.chain.hmac.secret:}")
    private String hmacSecret;

    @Value("${evidence.chain.anchor-interval:100}")
    private int anchorInterval;

    @Value("${evidence.chain.anchor-interval-ms:300000}")
    private long anchorIntervalMs;

    private final EvidenceChainEntryRepository entryRepository;
    private final EvidenceChainHeadRepository headRepository;
    private final EvidenceAnchorRepository anchorRepository;
    private final LightPointRepository lightPointRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvidenceChainService(EvidenceChainEntryRepository entryRepository,
                                EvidenceChainHeadRepository headRepository,
                                EvidenceAnchorRepository anchorRepository,
                                LightPointRepository lightPointRepository,
                                DeviceCommandRepository deviceCommandRepository) {
        this.entryRepository = entryRepository;
        this.headRepository = headRepository;
        this.anchorRepository = anchorRepository;
        this.lightPointRepository = lightPointRepository;
        this.deviceCommandRepository = deviceCommandRepository;
    }

    /** 启动 fail-fast：证据链启用但密钥缺失时直接拒绝启动。 */
    @PostConstruct
    void validateConfig() {
        if (chainEnabled && isBlank(hmacSecret)) {
            throw new IllegalStateException("证据链已启用但 HMAC Secret 未配置（EVIDENCE_HMAC_SECRET）");
        }
        if (chainEnabled && isBlank(currentKeyId)) {
            throw new IllegalStateException("证据链已启用但 HMAC keyId 未配置");
        }
    }

    // ==================== 追加 ====================

    @Transactional
    public EvidenceChainEntry append(String deviceCode, String eventType, long eventTs,
                                     String sourceType, Long sourceId, String canonicalPayload) {
        if (!chainEnabled) return null;
        if (isBlank(hmacSecret)) {
            throw new IllegalStateException("证据链已启用但 HMAC Secret 未配置，拒绝生成无认证码的证据");
        }
        EvidenceChainHead head = lockHead(deviceCode);
        long seq = head.getLatestSeq() + 1;
        String prevHash = head.getLatestHash();
        String entryHash = computeEntryHashByVersion(HASH_VERSION, PAYLOAD_VERSION, prevHash, deviceCode, seq,
                eventType, eventTs, sourceType, sourceId, canonicalPayload);
        String entryMac = computeEntryMacByVersion(MAC_VERSION, currentKeyId, deviceCode, seq, entryHash);

        EvidenceChainEntry entry = new EvidenceChainEntry();
        entry.setDeviceCode(deviceCode);
        entry.setSeq(seq);
        entry.setEventType(eventType);
        entry.setEventTs(eventTs);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setCanonicalPayload(canonicalPayload);
        entry.setPrevHash(prevHash);
        entry.setEntryHash(entryHash);
        entry.setHashVersion(HASH_VERSION);
        entry.setPayloadVersion(PAYLOAD_VERSION);
        entry.setMacVersion(MAC_VERSION);
        entry.setKeyId(currentKeyId);
        entry.setEntryMac(entryMac);
        entry.setCreatedAt(LocalDateTime.now());
        entryRepository.save(entry);

        head.setLatestSeq(seq);
        head.setLatestHash(entryHash);
        head.setUpdatedAt(LocalDateTime.now());
        head.setMacVersion(MAC_VERSION);
        head.setKeyId(currentKeyId);
        head.setHeadMac(computeHeadMacV1(currentKeyId, deviceCode, seq, entryHash));
        headRepository.save(head);
        return entry;
    }

    private EvidenceChainHead lockHead(String deviceCode) {
        headRepository.insertHeadIfAbsent(deviceCode);
        return headRepository.findForUpdate(deviceCode).orElseThrow();
    }

    // ==================== 规范载荷 ====================

    public String buildTelemetryPayload(LightPoint p) {
        ObjectNode n = mapper.createObjectNode();
        putText(n, "deviceCode", p.getDeviceCode());
        putLong(n, "ts", p.getTs());
        putNumber(n, "lux", p.getLux());
        putNumber(n, "temperature", p.getTemperature());
        putNumber(n, "voltage", p.getVoltage());
        putNumber(n, "current", p.getCurrent());
        putNumber(n, "power", p.getPower());
        putNumber(n, "energy", p.getEnergy());
        putText(n, "lampStatus", p.getLampStatus());
        return write(n);
    }

    public String buildCommandPayload(DeviceCommand c) {
        ObjectNode n = mapper.createObjectNode();
        putText(n, "commandId", c.getCommandId());
        putText(n, "deviceCode", c.getDeviceCode());
        putText(n, "action", c.getAction());
        putText(n, "mode", c.getMode());
        return write(n);
    }

    public String buildAckPayload(DeviceCommand c, String status) {
        ObjectNode n = mapper.createObjectNode();
        putText(n, "commandId", c.getCommandId());
        putText(n, "deviceCode", c.getDeviceCode());
        putText(n, "status", status);
        return write(n);
    }

    // ==================== 校验 ====================

    @Transactional(readOnly = true)
    public VerificationResult verify(String deviceCode) {
        if (chainEnabled && isBlank(hmacSecret)) {
            return new VerificationResult(STATUS_INVALID_MAC, S_NOT_CHECKED, S_DISABLED, S_NOT_CHECKED,
                    0, null, null, "证据链已启用但 HMAC Secret 缺失，无法校验",
                    ANCHOR_DISABLED, null, null, 0, false);
        }
        EvidenceChainHead head = headRepository.findById(deviceCode).orElse(null);
        long latestSeq = head == null ? 0 : head.getLatestSeq();

        // 信任 head 前先验证 Head MAC
        if (head != null && head.getLatestSeq() > 0) {
            String headMacIssue = verifyHeadMac(head, deviceCode);
            if (headMacIssue != null) {
                return result(STATUS_INVALID_CHAIN, S_INVALID, S_NOT_CHECKED, S_NOT_CHECKED, 0, null,
                        headMacIssue, headMacIssueReason(headMacIssue), new AnchorStatus(ANCHOR_NONE, null),
                        latestSeq, 0, false);
            }
        }

        AnchorStatus anchor = loadAnchorStatus(deviceCode);
        if (anchor.anchoredThroughSeq() != null && latestSeq < anchor.anchoredThroughSeq()) {
            return result(STATUS_INVALID_CHAIN, S_INVALID, S_NOT_CHECKED, S_NOT_CHECKED, 0, null,
                    BREAK_HEAD_ANCHOR_INCONSISTENT,
                    "链头 latestSeq=" + latestSeq + " 小于锚点 seq=" + anchor.anchoredThroughSeq(),
                    anchor, latestSeq, 0, false);
        }
        long unanchoredCount = anchor.anchoredThroughSeq() == null ? latestSeq : latestSeq - anchor.anchoredThroughSeq();
        boolean coverage = anchor.anchoredThroughSeq() != null && anchor.anchoredThroughSeq() == latestSeq;

        ChainScan scan = scanChain(deviceCode);
        if (scan.brokenSeq() != null) {
            if (isMacBreak(scan.breakType())) {
                return result(STATUS_INVALID_MAC, S_VALID, S_INVALID, S_NOT_CHECKED, scan.checked(), scan.brokenSeq(),
                        scan.breakType(), macBreakReason(scan), anchor, latestSeq, unanchoredCount, coverage);
            }
            return result(STATUS_INVALID_CHAIN, S_INVALID, S_NOT_CHECKED, S_NOT_CHECKED, scan.checked(), scan.brokenSeq(),
                    scan.breakType(), chainBreakReason(scan), anchor, latestSeq, unanchoredCount, coverage);
        }

        String headIssue = checkHeadConsistency(head, scan);
        if (headIssue != null) {
            return result(STATUS_INVALID_CHAIN, S_INVALID, S_NOT_CHECKED, S_NOT_CHECKED, scan.checked(),
                    headFirstBrokenSeq(headIssue, scan, head), headIssue, headIssueReason(headIssue, scan, head),
                    anchor, latestSeq, unanchoredCount, coverage);
        }

        SourceBreak source = verifyBusinessConsistency(deviceCode);
        if (source != null) {
            String reason = BREAK_SOURCE_MISSING.equals(source.breakType())
                    ? "业务源记录不存在：seq=" + source.seq() + "（可能被清理，需确认是否为保留策略删除）"
                    : "业务数据与证据不一致：seq=" + source.seq() + " 起的业务表字段被修改";
            return result(STATUS_INVALID_SOURCE, S_VALID, macStatus(scan), S_INVALID, scan.checked(), source.seq(),
                    source.breakType(), reason, anchor, latestSeq, unanchoredCount, coverage);
        }

        if (isInvalidAnchor(anchor.state())) {
            return result(STATUS_INVALID_ANCHOR, S_VALID, macStatus(scan), S_VALID, scan.checked(), null, null,
                    "锚点异常：" + anchor.state(), anchor, latestSeq, unanchoredCount, coverage);
        }

        String overall;
        if (ANCHOR_VALID.equals(anchor.state())) {
            overall = coverage ? STATUS_VALID_ANCHORED : STATUS_VALID_PARTIALLY_ANCHORED;
        } else {
            overall = STATUS_VALID_UNANCHORED;
        }
        return result(overall, S_VALID, macStatus(scan), S_VALID, scan.checked(), null, null, null,
                anchor, latestSeq, unanchoredCount, coverage);
    }

    private String macStatus(ChainScan scan) {
        if (!chainEnabled) return S_DISABLED;
        return isBlank(hmacSecret) ? S_DISABLED : S_VALID;
    }

    private boolean isMacBreak(String breakType) {
        return BREAK_ENTRY_MAC.equals(breakType) || BREAK_UNKNOWN_MAC_KEY.equals(breakType)
                || BREAK_UNSUPPORTED_MAC_VERSION.equals(breakType);
    }

    private String verifyHeadMac(EvidenceChainHead head, String deviceCode) {
        int macVersion = head.getMacVersion() == null ? LEGACY_MAC_VERSION : head.getMacVersion();
        if (macVersion != 1) return BREAK_UNSUPPORTED_MAC_VERSION;
        String expected = computeHeadMacV1(head.getKeyId(), deviceCode, head.getLatestSeq(), head.getLatestHash());
        if (expected == null || !constantTimeEquals(expected, head.getHeadMac())) {
            return BREAK_CHAIN_HEAD_MAC_INVALID;
        }
        return null;
    }

    private String headMacIssueReason(String headMacIssue) {
        return BREAK_UNSUPPORTED_MAC_VERSION.equals(headMacIssue)
                ? "链头 MAC 版本不支持"
                : "链头 HMAC 无效（latestSeq/latestHash 可能被回退）";
    }

    private ChainScan scanChain(String deviceCode) {
        String prevHash = "";
        long cursor = 0;
        long expectedSeq = 1;
        long scannedLastSeq = 0;
        String scannedLastHash = "";
        int checked = 0;
        while (true) {
            List<EvidenceChainEntry> page = nextPage(deviceCode, cursor);
            if (page.isEmpty()) break;
            for (EvidenceChainEntry e : page) {
                checked++;
                if (e.getSeq() != expectedSeq) {
                    return new ChainScan(e.getSeq(), BREAK_SEQUENCE_GAP, checked, scannedLastSeq, scannedLastHash);
                }
                expectedSeq++;
                if (!prevHash.equals(e.getPrevHash())) {
                    return new ChainScan(e.getSeq(), BREAK_CHAIN, checked, scannedLastSeq, scannedLastHash);
                }
                int hashVersion = e.getHashVersion() == null ? LEGACY_HASH_VERSION : e.getHashVersion();
                int payloadVersion = e.getPayloadVersion() == null ? LEGACY_PAYLOAD_VERSION : e.getPayloadVersion();
                if (hashVersion != 1) {
                    return new ChainScan(e.getSeq(), BREAK_UNSUPPORTED_HASH_VERSION, checked, scannedLastSeq, scannedLastHash);
                }
                if (payloadVersion != 1) {
                    return new ChainScan(e.getSeq(), BREAK_UNSUPPORTED_PAYLOAD_VERSION, checked, scannedLastSeq, scannedLastHash);
                }
                String expected = computeEntryHashV1(payloadVersion, prevHash, deviceCode, e.getSeq(), e.getEventType(),
                        e.getEventTs(), e.getSourceType(), e.getSourceId(), e.getCanonicalPayload());
                if (!expected.equals(e.getEntryHash())) {
                    return new ChainScan(e.getSeq(), BREAK_CHAIN, checked, scannedLastSeq, scannedLastHash);
                }
                if (!isBlank(hmacSecret)) {
                    int macVersion = e.getMacVersion() == null ? LEGACY_MAC_VERSION : e.getMacVersion();
                    if (macVersion != 1) {
                        return new ChainScan(e.getSeq(), BREAK_UNSUPPORTED_MAC_VERSION, checked, scannedLastSeq, scannedLastHash);
                    }
                    String expectedMac = computeEntryMacV1(e.getKeyId(), deviceCode, e.getSeq(), e.getEntryHash());
                    if (expectedMac == null) {
                        return new ChainScan(e.getSeq(), BREAK_UNKNOWN_MAC_KEY, checked, scannedLastSeq, scannedLastHash);
                    }
                    if (!constantTimeEquals(expectedMac, e.getEntryMac())) {
                        return new ChainScan(e.getSeq(), BREAK_ENTRY_MAC, checked, scannedLastSeq, scannedLastHash);
                    }
                }
                prevHash = e.getEntryHash();
                scannedLastSeq = e.getSeq();
                scannedLastHash = e.getEntryHash();
                cursor = e.getSeq();
            }
            if (page.size() < PAGE_SIZE) break;
        }
        return new ChainScan(null, null, checked, scannedLastSeq, scannedLastHash);
    }

    private String checkHeadConsistency(EvidenceChainHead head, ChainScan scan) {
        if (head == null) {
            return scan.checked() > 0 ? BREAK_HEAD_MISMATCH : null;
        }
        if (scan.checked() == 0 && head.getLatestSeq() > 0) return BREAK_TRUNCATED;
        if (scan.scannedLastSeq() < head.getLatestSeq()) return BREAK_TRUNCATED;
        if (scan.scannedLastSeq() > head.getLatestSeq()) return BREAK_HEAD_ROLLBACK;
        if (!scan.scannedLastHash().equals(head.getLatestHash())) return BREAK_HEAD_MISMATCH;
        return null;
    }

    private Long headFirstBrokenSeq(String headIssue, ChainScan scan, EvidenceChainHead head) {
        return switch (headIssue) {
            case BREAK_TRUNCATED -> scan.scannedLastSeq() + 1;
            case BREAK_HEAD_ROLLBACK -> scan.scannedLastSeq();
            case BREAK_HEAD_MISMATCH -> head == null ? null : head.getLatestSeq();
            default -> null;
        };
    }

    private String headIssueReason(String headIssue, ChainScan scan, EvidenceChainHead head) {
        return switch (headIssue) {
            case BREAK_TRUNCATED -> "链尾被截断：扫描到 seq=" + scan.scannedLastSeq() + "，链头为 " + head.getLatestSeq();
            case BREAK_HEAD_ROLLBACK -> "链头被回退：证据扫描到 seq=" + scan.scannedLastSeq() + "，链头仅为 " + head.getLatestSeq();
            case BREAK_HEAD_MISMATCH -> "链头与证据不一致（缺失或 hash 不符）";
            default -> headIssue;
        };
    }

    private String chainBreakReason(ChainScan scan) {
        return switch (scan.breakType()) {
            case BREAK_SEQUENCE_GAP -> "证据链 seq 不连续：期望 seq 缺失，首个异常点 seq=" + scan.brokenSeq();
            case BREAK_UNSUPPORTED_HASH_VERSION -> "不支持的 hash 版本：seq=" + scan.brokenSeq();
            case BREAK_UNSUPPORTED_PAYLOAD_VERSION -> "不支持的 payload 版本：seq=" + scan.brokenSeq();
            default -> "证据链哈希不匹配：seq=" + scan.brokenSeq() + " 起的证据被修改/插入/删除";
        };
    }

    private String macBreakReason(ChainScan scan) {
        return switch (scan.breakType()) {
            case BREAK_UNKNOWN_MAC_KEY -> "未知 MAC 密钥：seq=" + scan.brokenSeq();
            case BREAK_UNSUPPORTED_MAC_VERSION -> "不支持的 MAC 版本：seq=" + scan.brokenSeq();
            default -> "Entry MAC 不匹配：seq=" + scan.brokenSeq() + " 起的证据认证码被伪造或已失效";
        };
    }

    private boolean isInvalidAnchor(String state) {
        return ANCHOR_SIGNATURE_INVALID.equals(state) || ANCHOR_CHAIN_MISMATCH.equals(state) || ANCHOR_ENTRY_MISSING.equals(state);
    }

    private SourceBreak verifyBusinessConsistency(String deviceCode) {
        Map<Long, EvidenceChainEntry> latestAckByCommand = new HashMap<>();
        long cursor = 0;
        while (true) {
            List<EvidenceChainEntry> page = nextPage(deviceCode, cursor);
            if (page.isEmpty()) break;
            for (EvidenceChainEntry e : page) {
                cursor = e.getSeq();
                if (EVENT_ACK.equals(e.getEventType())) {
                    latestAckByCommand.put(e.getSourceId(), e);
                    continue;
                }
                String actual = resolveSourcePayload(e);
                if (actual == null) return new SourceBreak(e.getSeq(), BREAK_SOURCE_MISSING);
                if (!actual.equals(e.getCanonicalPayload())) return new SourceBreak(e.getSeq(), BREAK_SOURCE_MISMATCH);
            }
            if (page.size() < PAGE_SIZE) break;
        }
        for (Map.Entry<Long, EvidenceChainEntry> entry : latestAckByCommand.entrySet()) {
            DeviceCommand c = deviceCommandRepository.findById(entry.getKey()).orElse(null);
            if (c == null) return new SourceBreak(entry.getValue().getSeq(), BREAK_SOURCE_MISSING);
            String currentStatus = c.getStatus() == null ? null : c.getStatus().name();
            String actual = buildAckPayload(c, currentStatus);
            if (!actual.equals(entry.getValue().getCanonicalPayload())) {
                return new SourceBreak(entry.getValue().getSeq(), BREAK_SOURCE_MISMATCH);
            }
        }
        return null;
    }

    private String resolveSourcePayload(EvidenceChainEntry e) {
        if (SOURCE_LIGHT_POINT.equals(e.getSourceType())) {
            LightPoint p = lightPointRepository.findById(e.getSourceId()).orElse(null);
            return p == null ? null : buildTelemetryPayload(p);
        }
        if (SOURCE_DEVICE_COMMAND.equals(e.getSourceType()) && EVENT_COMMAND.equals(e.getEventType())) {
            DeviceCommand c = deviceCommandRepository.findById(e.getSourceId()).orElse(null);
            return c == null ? null : buildCommandPayload(c);
        }
        return null;
    }

    private List<EvidenceChainEntry> nextPage(String deviceCode, long cursor) {
        return entryRepository.findByDeviceCodeAndSeqGreaterThanOrderBySeqAsc(
                deviceCode, cursor, PageRequest.of(0, PAGE_SIZE));
    }

    // ==================== 查询接口（Controller 使用） ====================

    /** 轻量状态：只读链头 + 最新锚点元数据，不扫描整条链，但验证 Head HMAC（O(1)）。 */
    @Transactional(readOnly = true)
    public ChainMetadata getChainMetadata(String deviceCode) {
        boolean hasEvidence = entryRepository.existsByDeviceCode(deviceCode);
        EvidenceChainHead head = headRepository.findById(deviceCode).orElse(null);
        long latestSeq = head == null ? 0 : head.getLatestSeq();

        String headState;
        String metadataState = META_NORMAL;
        String metadataIssue = null;

        if (head == null) {
            headState = HEAD_STATE_NONE;
            if (hasEvidence) {
                metadataState = META_INCONSISTENT;
                metadataIssue = META_ISSUE_HEAD_MISSING;
            }
        } else if (head.getLatestSeq() <= 0) {
            headState = HEAD_STATE_NONE;
            if (hasEvidence) {
                metadataState = META_INCONSISTENT;
                metadataIssue = META_ISSUE_HEAD_MISSING;
            }
        } else if (verifyHeadMac(head, deviceCode) != null) {
            headState = HEAD_STATE_INVALID;
            metadataState = META_INCONSISTENT;
            metadataIssue = META_ISSUE_HEAD_MAC_INVALID;
        } else {
            headState = HEAD_STATE_VALID;
        }

        AnchorStatus anchor = loadAnchorStatus(deviceCode);
        Long unanchoredCount = null;
        boolean coverage = false;
        if (META_NORMAL.equals(metadataState)) {
            if (anchor.anchoredThroughSeq() != null && anchor.anchoredThroughSeq() > latestSeq) {
                metadataState = META_INCONSISTENT;
                metadataIssue = META_ISSUE_ANCHOR_AHEAD_OF_HEAD;
            } else if (anchor.anchoredThroughSeq() != null) {
                unanchoredCount = latestSeq - anchor.anchoredThroughSeq();
                coverage = anchor.anchoredThroughSeq() == latestSeq;
            } else {
                unanchoredCount = latestSeq;
            }
        }

        return new ChainMetadata(hasEvidence, latestSeq, headState, metadataState, metadataIssue,
                anchor.state(), anchor.anchoredThroughSeq(), unanchoredCount, coverage);
    }

    /** 证据条目分页查询：固定 seq 升序，支持 deviceCode + seq 范围 + 类型过滤。 */
    @Transactional(readOnly = true)
    public Page<EvidenceChainEntry> getEntries(String deviceCode, int page, int size, Long fromSeq, Long toSeq,
                                               String eventType, String sourceType) {
        Specification<EvidenceChainEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deviceCode"), deviceCode));
            if (fromSeq != null) predicates.add(cb.greaterThanOrEqualTo(root.get("seq"), fromSeq));
            if (toSeq != null) predicates.add(cb.lessThanOrEqualTo(root.get("seq"), toSeq));
            if (eventType != null && !eventType.isBlank()) predicates.add(cb.equal(root.get("eventType"), eventType));
            if (sourceType != null && !sourceType.isBlank()) predicates.add(cb.equal(root.get("sourceType"), sourceType));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return entryRepository.findAll(spec, PageRequest.of(page, size, Sort.by("seq").ascending()));
    }

    // ==================== HMAC 锚点 ====================

    @Scheduled(fixedDelayString = "${evidence.chain.anchor-interval-ms:300000}")
    @Transactional
    public void anchorAll() {
        if (isBlank(hmacSecret)) {
            log.warn("HMAC Secret 未配置，跳过证据链锚点生成");
            return;
        }
        long now = System.currentTimeMillis();
        int anchored = 0;
        for (EvidenceChainHead head : headRepository.findAll()) {
            if (head.getLatestSeq() <= 0) continue;
            String deviceCode = head.getDeviceCode();

            if (verifyHeadMac(head, deviceCode) != null) {
                log.error("设备 {} 链头 HMAC 无效，拒绝生成新锚点", deviceCode);
                continue;
            }
            EvidenceAnchor latest = anchorRepository.findFirstByDeviceCodeOrderBySeqDesc(deviceCode).orElse(null);
            if (latest != null && !ANCHOR_VALID.equals(anchorStateOf(latest, deviceCode))) {
                log.error("设备 {} 已有锚点完整性校验失败（ANCHOR_COMPROMISED），拒绝生成新锚点", deviceCode);
                continue;
            }
            EvidenceChainEntry tailEntry = entryRepository.findByDeviceCodeAndSeq(deviceCode, head.getLatestSeq()).orElse(null);
            if (tailEntry == null || !tailEntry.getEntryHash().equals(head.getLatestHash())) {
                log.error("设备 {} 链头与末条证据不一致，拒绝生成新锚点", deviceCode);
                continue;
            }
            int tailMacVersion = tailEntry.getMacVersion() == null ? LEGACY_MAC_VERSION : tailEntry.getMacVersion();
            String tailMac = computeEntryMacByVersion(tailMacVersion, tailEntry.getKeyId(), deviceCode,
                    tailEntry.getSeq(), tailEntry.getEntryHash());
            if (tailMac == null || !constantTimeEquals(tailMac, tailEntry.getEntryMac())) {
                log.error("设备 {} 末条证据 Entry MAC 无效，拒绝生成新锚点", deviceCode);
                continue;
            }

            long lastAnchoredSeq = latest == null ? 0 : latest.getSeq();
            long lastAnchoredAt = latest == null ? 0 : latest.getAnchoredAt();
            if (!shouldCreateAnchor(head.getLatestSeq(), lastAnchoredSeq, lastAnchoredAt, now)) continue;

            EvidenceAnchor anchor = new EvidenceAnchor();
            anchor.setDeviceCode(deviceCode);
            anchor.setSeq(head.getLatestSeq());
            anchor.setEntryHash(head.getLatestHash());
            anchor.setAnchoredAt(now);
            anchor.setCreatedAt(LocalDateTime.now());
            anchor.setSignature(computeAnchorSignature(deviceCode, head.getLatestSeq(), head.getLatestHash(), now));
            anchorRepository.save(anchor);
            anchored++;
        }
        if (anchored > 0) log.info("已生成 {} 台设备的证据链锚点", anchored);
    }

    boolean shouldCreateAnchor(long latestSeq, long lastAnchoredSeq, long lastAnchoredAt, long now) {
        long newCount = latestSeq - lastAnchoredSeq;
        boolean countReached = newCount >= anchorInterval;
        boolean timeReached = newCount > 0 && (now - lastAnchoredAt) >= anchorIntervalMs;
        return countReached || timeReached;
    }

    private AnchorStatus loadAnchorStatus(String deviceCode) {
        if (isBlank(hmacSecret)) return new AnchorStatus(ANCHOR_DISABLED, null);
        EvidenceAnchor anchor = anchorRepository.findFirstByDeviceCodeOrderBySeqDesc(deviceCode).orElse(null);
        if (anchor == null) return new AnchorStatus(ANCHOR_NONE, null);
        String state = anchorStateOf(anchor, deviceCode);
        return new AnchorStatus(state, ANCHOR_VALID.equals(state) ? anchor.getSeq() : null);
    }

    private String anchorStateOf(EvidenceAnchor anchor, String deviceCode) {
        String expected = computeAnchorSignature(deviceCode, anchor.getSeq(), anchor.getEntryHash(), anchor.getAnchoredAt());
        if (expected == null || !constantTimeEquals(expected, anchor.getSignature())) return ANCHOR_SIGNATURE_INVALID;
        EvidenceChainEntry entry = entryRepository.findByDeviceCodeAndSeq(deviceCode, anchor.getSeq()).orElse(null);
        if (entry == null) return ANCHOR_ENTRY_MISSING;
        if (!entry.getEntryHash().equals(anchor.getEntryHash())) return ANCHOR_CHAIN_MISMATCH;
        return ANCHOR_VALID;
    }

    private String computeAnchorSignature(String deviceCode, long seq, String entryHash, long anchoredAt) {
        if (isBlank(hmacSecret)) return null;
        String input = "{\"domain\":\"" + DOMAIN_ANCHOR + "\""
                + ",\"deviceCode\":" + jsonString(deviceCode)
                + ",\"seq\":" + seq
                + ",\"entryHash\":" + jsonString(entryHash)
                + ",\"anchoredAt\":" + anchoredAt + "}";
        return hmacHex(hmacSecret, input);
    }

    // ==================== 哈希 / MAC 工具 ====================

    private String computeEntryHashByVersion(int hashVersion, int payloadVersion, String prevHash, String deviceCode,
                                             long seq, String eventType, long eventTs, String sourceType,
                                             Long sourceId, String canonicalPayload) {
        return switch (hashVersion) {
            case 1 -> computeEntryHashV1(payloadVersion, prevHash, deviceCode, seq, eventType, eventTs,
                    sourceType, sourceId, canonicalPayload);
            default -> null;
        };
    }

    /** V1：hashVersion 固定为 1（不用可变的当前常量），payloadVersion 未知则 fail closed。 */
    String computeEntryHashV1(int payloadVersion, String prevHash, String deviceCode, long seq, String eventType,
                              long eventTs, String sourceType, Long sourceId, String canonicalPayload) {
        if (payloadVersion != 1) return null;
        String input = "{\"hashVersion\":1"
                + ",\"payloadVersion\":" + payloadVersion
                + ",\"prevHash\":" + jsonString(prevHash)
                + ",\"deviceCode\":" + jsonString(deviceCode)
                + ",\"seq\":" + seq
                + ",\"eventType\":" + jsonString(eventType)
                + ",\"eventTs\":" + eventTs
                + ",\"sourceType\":" + jsonString(sourceType)
                + ",\"sourceId\":" + (sourceId == null ? "null" : sourceId)
                + ",\"payload\":" + canonicalPayload + "}";
        return sha256Hex(input);
    }

    private String computeEntryMacByVersion(int macVersion, String keyId, String deviceCode, long seq, String entryHash) {
        return switch (macVersion) {
            case 1 -> computeEntryMacV1(keyId, deviceCode, seq, entryHash);
            default -> null;
        };
    }

    String computeEntryMacV1(String keyId, String deviceCode, long seq, String entryHash) {
        String secret = resolveSecret(keyId);
        if (secret == null) return null;
        String input = "{\"domain\":\"" + DOMAIN_ENTRY_MAC + "\""
                + ",\"macVersion\":1"
                + ",\"keyId\":" + jsonString(keyId)
                + ",\"deviceCode\":" + jsonString(deviceCode)
                + ",\"seq\":" + seq
                + ",\"entryHash\":" + jsonString(entryHash) + "}";
        return hmacHex(secret, input);
    }

    String computeHeadMacV1(String keyId, String deviceCode, long latestSeq, String latestHash) {
        String secret = resolveSecret(keyId);
        if (secret == null) return null;
        String input = "{\"domain\":\"" + DOMAIN_CHAIN_HEAD + "\""
                + ",\"macVersion\":1"
                + ",\"keyId\":" + jsonString(keyId)
                + ",\"deviceCode\":" + jsonString(deviceCode)
                + ",\"latestSeq\":" + latestSeq
                + ",\"latestHash\":" + jsonString(latestHash) + "}";
        return hmacHex(secret, input);
    }

    /** 仅对已知 keyId 返回对应 Secret；未知 keyId 不拿当前密钥验证。 */
    private String resolveSecret(String keyId) {
        if (currentKeyId != null && currentKeyId.equals(keyId)) return hmacSecret;
        return null;
    }

    private String hmacHex(String secret, String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return toHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    boolean constantTimeEquals(String hexA, String hexB) {
        if (hexA == null || hexB == null) return false;
        byte[] a = hexToBytes(hexA);
        byte[] b = hexToBytes(hexB);
        if (a == null || b == null || a.length != b.length) return false;
        return MessageDigest.isEqual(a, b);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        if (len % 2 != 0) return null;
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String jsonString(String value) {
        return "\"" + (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ==================== 载荷构建工具 ====================

    private void putNumber(ObjectNode n, String field, Double value) {
        if (value == null) n.putNull(field); else n.put(field, value);
    }

    private void putLong(ObjectNode n, String field, Long value) {
        if (value == null) n.putNull(field); else n.put(field, value);
    }

    private void putText(ObjectNode n, String field, String value) {
        if (value == null) n.putNull(field); else n.put(field, value);
    }

    private String write(ObjectNode n) {
        try {
            return mapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new IllegalStateException("规范载荷序列化失败", e);
        }
    }

    // ==================== 数据结构 ====================

    private VerificationResult result(String overall, String chainStatus, String macStatus, String sourceStatus,
                                      int checked, Long firstBrokenSeq, String breakType, String reason,
                                      AnchorStatus anchor, long latestSeq, long unanchoredCount, boolean coverage) {
        return new VerificationResult(overall, chainStatus, macStatus, sourceStatus, checked, firstBrokenSeq,
                breakType, reason, anchor.state(), latestSeq, anchor.anchoredThroughSeq(), unanchoredCount, coverage);
    }

    private record AnchorStatus(String state, Long anchoredThroughSeq) {}

    private record SourceBreak(Long seq, String breakType) {}

    private record ChainScan(Long brokenSeq, String breakType, int checked, long scannedLastSeq, String scannedLastHash) {}

    public record ChainMetadata(boolean hasEvidence, long latestSeq, String headState, String metadataState,
                                String metadataIssue, String anchorState, Long anchoredThroughSeq,
                                Long unanchoredCount, boolean anchorCoverageComplete) {}

    public record VerificationResult(String overallStatus, String chainStatus, String macStatus, String sourceStatus,
                                     int checkedCount, Long firstBrokenSeq, String breakType, String reason,
                                     String anchorState, Long latestSeq, Long anchoredThroughSeq,
                                     long unanchoredCount, boolean anchorCoverageComplete) {}
}
