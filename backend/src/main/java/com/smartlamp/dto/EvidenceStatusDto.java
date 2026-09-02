package com.smartlamp.dto;

import com.smartlamp.service.EvidenceChainService;
import lombok.Data;

/** 证据链轻量状态（只读链头 + 最新锚点元数据，不扫描整条链，但验证 Head HMAC）。 */
@Data
public class EvidenceStatusDto {
    private String deviceCode;
    private boolean hasEvidence;
    private long latestSeq;
    private String headState;
    private String metadataState;
    private String metadataIssue;
    private String anchorState;
    private Long anchoredThroughSeq;
    private Long unanchoredCount;
    private boolean anchorCoverageComplete;
    /** 恒为 false：本接口不扫描整条证据链，不能表达 VALID/INVALID 结论 */
    private boolean verificationPerformed;

    public static EvidenceStatusDto from(String deviceCode, EvidenceChainService.ChainMetadata meta) {
        EvidenceStatusDto dto = new EvidenceStatusDto();
        dto.setDeviceCode(deviceCode);
        dto.setHasEvidence(meta.hasEvidence());
        dto.setLatestSeq(meta.latestSeq());
        dto.setHeadState(meta.headState());
        dto.setMetadataState(meta.metadataState());
        dto.setMetadataIssue(meta.metadataIssue());
        dto.setAnchorState(meta.anchorState());
        dto.setAnchoredThroughSeq(meta.anchoredThroughSeq());
        dto.setUnanchoredCount(meta.unanchoredCount());
        dto.setAnchorCoverageComplete(meta.anchorCoverageComplete());
        dto.setVerificationPerformed(false);
        return dto;
    }
}
