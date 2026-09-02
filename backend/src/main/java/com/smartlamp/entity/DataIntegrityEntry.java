package com.smartlamp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不保存业务明文的轻量完整性日志。每台设备按 sequenceNo 串联，chainMac 同时认证
 * 事件摘要、业务源摘要和前一条 MAC，既能发现字段篡改，也能发现插入、删除和乱序。
 */
@Data
@Entity
@Table(name = "data_integrity_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_integrity_device_seq",
                columnNames = {"device_code", "sequence_no"}),
        indexes = {
                @Index(name = "idx_integrity_device_seq", columnList = "device_code,sequence_no"),
                @Index(name = "idx_integrity_source", columnList = "source_type,source_id")
        })
public class DataIntegrityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, length = 128)
    private String deviceCode;

    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "occurred_at", nullable = false)
    private Long occurredAt;

    @Column(name = "event_digest", nullable = false, length = 64)
    private String eventDigest;

    @Column(name = "source_digest", nullable = false, length = 64)
    private String sourceDigest;

    @Column(name = "previous_mac", nullable = false, length = 64)
    private String previousMac;

    @Column(name = "chain_mac", nullable = false, length = 64)
    private String chainMac;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion = 1;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
