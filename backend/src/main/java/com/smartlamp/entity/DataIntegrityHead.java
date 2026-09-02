package com.smartlamp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 每台设备一条受 HMAC 认证的链头，用于并发串行化并检测链尾删除/回退。 */
@Data
@Entity
@Table(name = "data_integrity_head")
public class DataIntegrityHead {

    @Id
    @Column(name = "device_code", length = 128)
    private String deviceCode;

    @Column(name = "latest_sequence", nullable = false)
    private Long latestSequence;

    @Column(name = "latest_mac", nullable = false, length = 64)
    private String latestMac;

    @Column(name = "head_mac", length = 64)
    private String headMac;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion = 1;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
