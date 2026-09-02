package com.smartlamp.dto;

import com.smartlamp.entity.EvidenceChainEntry;
import lombok.Data;

import java.time.LocalDateTime;

/** 证据条目详情（不含任何应用侧密钥；hash/mac 本身是审计证据可展示）。 */
@Data
public class EvidenceEntryDto {
    private Long seq;
    private String deviceCode;
    private String eventType;
    private Long eventTs;
    private String sourceType;
    private Long sourceId;
    private String canonicalPayload;
    private String entryHash;
    private String prevHash;
    private Integer hashVersion;
    private Integer payloadVersion;
    private Integer macVersion;
    private String keyId;
    private String entryMac;
    private LocalDateTime createdAt;

    public static EvidenceEntryDto from(EvidenceChainEntry e) {
        EvidenceEntryDto dto = new EvidenceEntryDto();
        dto.setSeq(e.getSeq());
        dto.setDeviceCode(e.getDeviceCode());
        dto.setEventType(e.getEventType());
        dto.setEventTs(e.getEventTs());
        dto.setSourceType(e.getSourceType());
        dto.setSourceId(e.getSourceId());
        dto.setCanonicalPayload(e.getCanonicalPayload());
        dto.setEntryHash(e.getEntryHash());
        dto.setPrevHash(e.getPrevHash());
        dto.setHashVersion(e.getHashVersion());
        dto.setPayloadVersion(e.getPayloadVersion());
        dto.setMacVersion(e.getMacVersion());
        dto.setKeyId(e.getKeyId());
        dto.setEntryMac(e.getEntryMac());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
