package com.smartlamp.dto;

import com.smartlamp.service.EvidenceChainService;
import lombok.Data;

/** 完整验证结果：完整保留 Stage 2 定义的状态语义，不压缩成单个 boolean。 */
@Data
public class EvidenceVerifyDto {
    private String deviceCode;
    private long verifiedAt;
    private boolean hasEvidence;
    private String overallStatus;
    private String chainStatus;
    private String macStatus;
    private String sourceStatus;
    private String anchorState;
    private int checkedCount;
    private Long firstBrokenSeq;
    private String breakType;
    private String reason;
    private Long latestSeq;
    private Long anchoredThroughSeq;
    private long unanchoredCount;
    private boolean anchorCoverageComplete;

    public static EvidenceVerifyDto from(String deviceCode, EvidenceChainService.VerificationResult r) {
        EvidenceVerifyDto dto = new EvidenceVerifyDto();
        dto.setDeviceCode(deviceCode);
        dto.setVerifiedAt(System.currentTimeMillis());
        dto.setHasEvidence(r.checkedCount() > 0);
        dto.setOverallStatus(r.overallStatus());
        dto.setChainStatus(r.chainStatus());
        dto.setMacStatus(r.macStatus());
        dto.setSourceStatus(r.sourceStatus());
        dto.setAnchorState(r.anchorState());
        dto.setCheckedCount(r.checkedCount());
        dto.setFirstBrokenSeq(r.firstBrokenSeq());
        dto.setBreakType(r.breakType());
        dto.setReason(r.reason());
        dto.setLatestSeq(r.latestSeq());
        dto.setAnchoredThroughSeq(r.anchoredThroughSeq());
        dto.setUnanchoredCount(r.unanchoredCount());
        dto.setAnchorCoverageComplete(r.anchorCoverageComplete());
        return dto;
    }
}
