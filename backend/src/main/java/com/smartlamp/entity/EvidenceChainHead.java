package com.smartlamp.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 每台设备一条链头记录：记录最新 seq 与最新 hash，用于并发追加时的悲观锁定位，
 * 避免同设备遥测与命令并发到达时引用同一个 prevHash。
 */
@Data
@Entity
@Table(name = "evidence_chain_head")
public class EvidenceChainHead {

    @Id
    @Column(name = "device_code", length = 128)
    private String deviceCode;

    @Column(name = "latest_seq", nullable = false)
    private Long latestSeq;

    @Column(name = "latest_hash", nullable = false, length = 64)
    private String latestHash;

    /** 链头 HMAC：latestSeq>0 时必须有合法认证码，防止未锚定尾段删除后回退 head */
    @Column(name = "head_mac", length = 64)
    private String headMac;

    @Column(name = "mac_version", nullable = false)
    private Integer macVersion = 1;

    @Column(name = "key_id", nullable = false, length = 32)
    private String keyId = "v1";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
