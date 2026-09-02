package com.smartlamp.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 遥测/命令/回执的防篡改证据链条目（每台设备一条链，按 seq 递增串联）。
 * 与业务表（light_point / device_command）分离存储，校验时既核对链哈希，
 * 也按 source_type + source_id 回读业务表做一致性比对。
 */
@Data
@Entity
@Table(name = "evidence_chain",
        uniqueConstraints = @UniqueConstraint(name = "uk_evidence_chain_device_seq", columnNames = {"device_code", "seq"}),
        indexes = {
                @Index(name = "idx_evidence_chain_device_seq", columnList = "device_code, seq"),
                @Index(name = "idx_evidence_chain_source", columnList = "source_type, source_id")
        })
public class EvidenceChainEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, length = 128)
    private String deviceCode;

    /** 该设备证据链序号（系统见证/入库顺序，非设备发生时间顺序） */
    @Column(nullable = false)
    private Long seq;

    /** TELEMETRY / COMMAND / ACK */
    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    @Column(name = "event_ts", nullable = false)
    private Long eventTs;

    /** LIGHT_POINT / DEVICE_COMMAND */
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    /** 被摘要的规范载荷（JSON，字段顺序固定） */
    @Column(name = "canonical_payload", nullable = false, length = 4000)
    private String canonicalPayload;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(name = "entry_hash", nullable = false, length = 64)
    private String entryHash;

    /** 哈希/载荷版本号，随每条证据持久化，未来格式升级时按版本重算 */
    @Column(name = "hash_version", nullable = false)
    private Integer hashVersion = 1;

    @Column(name = "payload_version", nullable = false)
    private Integer payloadVersion = 1;

    /** 每条证据的独立 HMAC 认证码（应用侧密钥），即使重算普通 SHA 链也无法伪造 */
    @Column(name = "entry_mac", nullable = false, length = 64)
    private String entryMac;

    @Column(name = "mac_version", nullable = false)
    private Integer macVersion = 1;

    @Column(name = "key_id", nullable = false, length = 32)
    private String keyId = "v1";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
