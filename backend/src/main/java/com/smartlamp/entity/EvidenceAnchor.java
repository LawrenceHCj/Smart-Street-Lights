package com.smartlamp.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * HMAC 锚点：只追加、业务代码不更新不删除，用于对抗"整条链重算"。
 * signature = HMAC-SHA256(secret, deviceCode|seq|entryHash|anchoredAt)，secret 走环境变量不落库。
 */
@Data
@Entity
@Table(name = "evidence_anchor",
        indexes = @Index(name = "idx_evidence_anchor_device", columnList = "device_code, seq"))
public class EvidenceAnchor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, length = 128)
    private String deviceCode;

    @Column(nullable = false)
    private Long seq;

    @Column(name = "entry_hash", nullable = false, length = 64)
    private String entryHash;

    @Column(nullable = false, length = 64)
    private String signature;

    @Column(name = "anchored_at", nullable = false)
    private Long anchoredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
