package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.EvidenceEntryDto;
import com.smartlamp.dto.EvidenceVerifyDto;
import com.smartlamp.entity.EvidenceChainEntry;
import com.smartlamp.service.EvidenceChainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceControllerTest {
    @Mock private EvidenceChainService evidenceChainService;
    private EvidenceController controller;

    @BeforeEach
    void setUp() {
        controller = new EvidenceController(evidenceChainService);
    }

    @Test
    void negativePageIsRejected() {
        ApiResponse<Page<EvidenceEntryDto>> r = controller.entries("SL-001", -1, 50, null, null, null, null);
        assertThat(r.getCode()).isEqualTo(400);
        verify(evidenceChainService, never()).getEntries(any(), anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void zeroSizeIsRejected() {
        ApiResponse<Page<EvidenceEntryDto>> r = controller.entries("SL-001", 0, 0, null, null, null, null);
        assertThat(r.getCode()).isEqualTo(400);
    }

    @Test
    void tooLargeSizeIsRejected() {
        ApiResponse<Page<EvidenceEntryDto>> r = controller.entries("SL-001", 0, 201, null, null, null, null);
        assertThat(r.getCode()).isEqualTo(400);
    }

    @Test
    void fromSeqBelowOneIsRejected() {
        ApiResponse<Page<EvidenceEntryDto>> r = controller.entries("SL-001", 0, 50, 0L, null, null, null);
        assertThat(r.getCode()).isEqualTo(400);
    }

    @Test
    void toSeqBelowFromSeqIsRejected() {
        ApiResponse<Page<EvidenceEntryDto>> r = controller.entries("SL-001", 0, 50, 10L, 5L, null, null);
        assertThat(r.getCode()).isEqualTo(400);
    }

    @Test
    void verifyPreservesAllStatusFields() {
        EvidenceChainService.VerificationResult result = new EvidenceChainService.VerificationResult(
                "INVALID_MAC", "VALID", "INVALID", "NOT_CHECKED", 10, 5L, "ENTRY_MAC_INVALID", "reason",
                "NONE", 100L, null, 100L, false);
        when(evidenceChainService.verify("SL-001")).thenReturn(result);

        ApiResponse<EvidenceVerifyDto> r = controller.verify("SL-001");

        assertThat(r.getCode()).isEqualTo(0);
        EvidenceVerifyDto dto = r.getData();
        assertThat(dto.getOverallStatus()).isEqualTo("INVALID_MAC");
        assertThat(dto.getChainStatus()).isEqualTo("VALID");
        assertThat(dto.getMacStatus()).isEqualTo("INVALID");
        assertThat(dto.getSourceStatus()).isEqualTo("NOT_CHECKED");
        assertThat(dto.getAnchorState()).isEqualTo("NONE");
        assertThat(dto.getBreakType()).isEqualTo("ENTRY_MAC_INVALID");
        assertThat(dto.getFirstBrokenSeq()).isEqualTo(5L);
    }

    @Test
    void entriesMapsPageToDto() {
        EvidenceChainEntry entry = new EvidenceChainEntry();
        entry.setSeq(1L);
        entry.setDeviceCode("SL-001");
        entry.setEventType("TELEMETRY");
        entry.setEventTs(123L);
        entry.setSourceType("LIGHT_POINT");
        entry.setSourceId(1L);
        entry.setCanonicalPayload("{}");
        entry.setEntryHash("hash");
        entry.setPrevHash("");
        entry.setEntryMac("mac");
        entry.setHashVersion(1);
        entry.setPayloadVersion(1);
        entry.setMacVersion(1);
        entry.setKeyId("v1");
        Page<EvidenceChainEntry> page = new PageImpl<>(List.of(entry));
        when(evidenceChainService.getEntries(eq("SL-001"), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(page);

        ApiResponse<Page<EvidenceEntryDto>> r = controller.entries("SL-001", 0, 50, null, null, null, null);

        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getData().getContent()).hasSize(1);
        assertThat(r.getData().getContent().get(0).getSeq()).isEqualTo(1L);
        assertThat(r.getData().getContent().get(0).getEntryMac()).isEqualTo("mac");
    }

    @Test
    void verifyMapsInvalidMac() {
        when(evidenceChainService.verify("SL-001")).thenReturn(verifyResult("INVALID_MAC"));

        assertThat(controller.verify("SL-001").getData().getOverallStatus()).isEqualTo("INVALID_MAC");
    }

    @Test
    void verifyMapsInvalidChain() {
        when(evidenceChainService.verify("SL-001")).thenReturn(verifyResult("INVALID_CHAIN"));

        assertThat(controller.verify("SL-001").getData().getOverallStatus()).isEqualTo("INVALID_CHAIN");
    }

    @Test
    void verifyMapsInvalidAnchor() {
        when(evidenceChainService.verify("SL-001")).thenReturn(verifyResult("INVALID_ANCHOR"));

        assertThat(controller.verify("SL-001").getData().getOverallStatus()).isEqualTo("INVALID_ANCHOR");
    }

    @Test
    void verifyMapsInvalidSource() {
        when(evidenceChainService.verify("SL-001")).thenReturn(verifyResult("INVALID_SOURCE"));

        assertThat(controller.verify("SL-001").getData().getOverallStatus()).isEqualTo("INVALID_SOURCE");
    }

    @Test
    void verifyHasEvidenceTrueWhenEntriesExistButHeadMissing() {
        EvidenceChainService.VerificationResult result = new EvidenceChainService.VerificationResult(
                "INVALID_CHAIN", "INVALID", "NOT_CHECKED", "NOT_CHECKED", 3, null, "CHAIN_HEAD_MISMATCH", "reason",
                "NONE", 0L, null, 0L, false);
        when(evidenceChainService.verify("SL-001")).thenReturn(result);

        EvidenceVerifyDto dto = controller.verify("SL-001").getData();

        assertThat(dto.isHasEvidence()).isTrue();
        assertThat(dto.getOverallStatus()).isEqualTo("INVALID_CHAIN");
    }

    private EvidenceChainService.VerificationResult verifyResult(String overall) {
        return new EvidenceChainService.VerificationResult(overall, "VALID", "VALID", "VALID", 1, null, null, null,
                "NONE", 1L, null, 1L, false);
    }
}
