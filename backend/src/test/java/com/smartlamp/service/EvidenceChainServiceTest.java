package com.smartlamp.service;

import com.smartlamp.entity.EvidenceAnchor;
import com.smartlamp.entity.EvidenceChainEntry;
import com.smartlamp.entity.EvidenceChainHead;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.EvidenceAnchorRepository;
import com.smartlamp.repository.EvidenceChainEntryRepository;
import com.smartlamp.repository.EvidenceChainHeadRepository;
import com.smartlamp.repository.LightPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceChainServiceTest {
    @Mock private EvidenceChainEntryRepository entryRepository;
    @Mock private EvidenceChainHeadRepository headRepository;
    @Mock private EvidenceAnchorRepository anchorRepository;
    @Mock private LightPointRepository lightPointRepository;
    @Mock private DeviceCommandRepository deviceCommandRepository;

    private EvidenceChainService service;
    private EvidenceChainHead head;
    private List<EvidenceChainEntry> savedEntries;

    @BeforeEach
    void setUp() {
        service = new EvidenceChainService(entryRepository, headRepository, anchorRepository,
                lightPointRepository, deviceCommandRepository);
        ReflectionTestUtils.setField(service, "chainEnabled", true);
        ReflectionTestUtils.setField(service, "hmacSecret", "test-secret");
        ReflectionTestUtils.setField(service, "currentKeyId", "v1");
        ReflectionTestUtils.setField(service, "anchorInterval", 100);
        ReflectionTestUtils.setField(service, "anchorIntervalMs", 300000);

        head = new EvidenceChainHead();
        head.setDeviceCode("SL-001");
        head.setLatestSeq(0L);
        head.setLatestHash("");
        savedEntries = new ArrayList<>();

        when(headRepository.findForUpdate("SL-001")).thenReturn(Optional.of(head));
        when(headRepository.findById("SL-001")).thenReturn(Optional.of(head));
        when(entryRepository.save(any(EvidenceChainEntry.class))).thenAnswer(inv -> {
            EvidenceChainEntry e = inv.getArgument(0);
            e.setId((long) (savedEntries.size() + 1));
            savedEntries.add(e);
            return e;
        });
        when(entryRepository.findByDeviceCodeAndSeqGreaterThanOrderBySeqAsc(eq("SL-001"), anyLong(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    long seq = inv.getArgument(1);
                    Pageable pageable = inv.getArgument(2);
                    return savedEntries.stream()
                            .filter(e -> e.getSeq() > seq)
                            .skip(pageable.getOffset())
                            .limit(pageable.getPageSize())
                            .toList();
                });
        when(entryRepository.findByDeviceCodeAndSeq(eq("SL-001"), anyLong())).thenReturn(Optional.empty());
        when(entryRepository.existsByDeviceCode("SL-001")).thenReturn(false);
        when(anchorRepository.findFirstByDeviceCodeOrderBySeqDesc("SL-001")).thenReturn(Optional.empty());
    }

    // ==================== 配置 ====================

    @Test
    void validateConfigFailsWhenEnabledButSecretEmpty() {
        ReflectionTestUtils.setField(service, "chainEnabled", true);
        ReflectionTestUtils.setField(service, "hmacSecret", "");

        assertThatThrownBy(() -> service.validateConfig()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appendFailsFastWhenSecretEmpty() {
        ReflectionTestUtils.setField(service, "hmacSecret", "");
        LightPoint point = point(85.0);

        assertThatThrownBy(() -> appendTelemetry(point)).isInstanceOf(IllegalStateException.class);
    }

    // ==================== 整体状态 ====================

    @Test
    void verifyReportsValidUnanchoredForIntactChain() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_VALID_UNANCHORED);
        assertThat(result.chainStatus()).isEqualTo(EvidenceChainService.S_VALID);
        assertThat(result.macStatus()).isEqualTo(EvidenceChainService.S_VALID);
        assertThat(result.sourceStatus()).isEqualTo(EvidenceChainService.S_VALID);
        assertThat(result.anchorState()).isEqualTo(EvidenceChainService.ANCHOR_NONE);
    }

    // ==================== 链完整性 ====================

    @Test
    void verifyDetectsChainTamper() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        savedEntries.get(0).setCanonicalPayload("{\"tampered\":true}");

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_INVALID_CHAIN);
        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_CHAIN);
    }

    @Test
    void verifyDetectsSequenceGap() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        appendTelemetry(point);

        savedEntries.get(1).setSeq(3L);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_SEQUENCE_GAP);
    }

    @Test
    void verifyDetectsPrevHashTamper() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        appendTelemetry(point);

        savedEntries.get(1).setPrevHash("wrong");

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_CHAIN);
    }

    @Test
    void verifyDetectsTailTruncation() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        appendTelemetry(point);
        appendTelemetry(point);

        savedEntries.remove(savedEntries.size() - 1);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_TRUNCATED);
    }

    @Test
    void verifyDetectsUnsupportedHashVersion() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        savedEntries.get(0).setHashVersion(2);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_UNSUPPORTED_HASH_VERSION);
    }

    @Test
    void verifyDetectsUnsupportedPayloadVersion() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        savedEntries.get(0).setPayloadVersion(2);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_UNSUPPORTED_PAYLOAD_VERSION);
    }

    @Test
    void verifyDetectsUnsupportedMacVersion() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        savedEntries.get(0).setMacVersion(2);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_INVALID_MAC);
        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_UNSUPPORTED_MAC_VERSION);
    }

    // ==================== Entry MAC ====================

    @Test
    void verifyDetectsMacInvalidOnRecomputedChain() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        EvidenceChainEntry e = savedEntries.get(0);
        String newPayload = "{\"tampered\":true}";
        String newHash = service.computeEntryHashV1(e.getPayloadVersion(), e.getPrevHash(), e.getDeviceCode(), e.getSeq(),
                e.getEventType(), e.getEventTs(), e.getSourceType(), e.getSourceId(), newPayload);
        e.setCanonicalPayload(newPayload);
        e.setEntryHash(newHash);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_INVALID_MAC);
        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_ENTRY_MAC);
    }

    @Test
    void verifyDetectsEntryMacTamper() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        savedEntries.get(0).setEntryMac("deadbeef");

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_ENTRY_MAC);
    }

    @Test
    void verifyDetectsUnknownMacKey() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        savedEntries.get(0).setKeyId("unknown");

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_UNKNOWN_MAC_KEY);
    }

    // ==================== ChainHead MAC ====================

    @Test
    void verifyDetectsHeadMacInvalidWhenLatestSeqTampered() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        head.setLatestSeq(2L);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_CHAIN_HEAD_MAC_INVALID);
    }

    @Test
    void verifyDetectsHeadMacInvalidWhenLatestHashTampered() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        head.setLatestHash("tampered");

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_CHAIN_HEAD_MAC_INVALID);
    }

    @Test
    void verifyDetectsHeadMacInvalidWhenHeadMacTampered() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        head.setHeadMac("deadbeef");

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_CHAIN_HEAD_MAC_INVALID);
    }

    @Test
    void verifyDetectsHeadMacInvalidAfterTailDeleteAndRollback() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        appendTelemetry(point);
        appendTelemetry(point);

        savedEntries.remove(savedEntries.size() - 1);
        head.setLatestSeq(2L);
        head.setLatestHash(savedEntries.get(1).getEntryHash());

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_CHAIN_HEAD_MAC_INVALID);
    }

    @Test
    void verifyDetectsHeadRollbackWithRecomputedMac() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        appendTelemetry(point);
        appendTelemetry(point);

        head.setLatestSeq(2L);
        head.setLatestHash(savedEntries.get(1).getEntryHash());
        head.setHeadMac(service.computeHeadMacV1("v1", "SL-001", 2L, savedEntries.get(1).getEntryHash()));

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_HEAD_ROLLBACK);
    }

    // ==================== 业务一致性 ====================

    @Test
    void verifyDetectsBusinessSourceMismatch() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        point.setLux(999.0);

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_INVALID_SOURCE);
        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_SOURCE_MISMATCH);
    }

    // ==================== 锚点 ====================

    @Test
    void verifyReportsValidAnchored() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        long anchoredAt = 1755835200000L;
        String entryHash = savedEntries.get(0).getEntryHash();
        when(anchorRepository.findFirstByDeviceCodeOrderBySeqDesc("SL-001"))
                .thenReturn(Optional.of(anchor(1L, entryHash, hmac(1L, entryHash, anchoredAt), anchoredAt)));
        when(entryRepository.findByDeviceCodeAndSeq("SL-001", 1L)).thenReturn(Optional.of(savedEntries.get(0)));

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_VALID_ANCHORED);
        assertThat(result.anchorCoverageComplete()).isTrue();
    }

    @Test
    void verifyReportsPartiallyAnchored() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        appendTelemetry(point);

        long anchoredAt = 1755835200000L;
        String hash1 = savedEntries.get(0).getEntryHash();
        when(anchorRepository.findFirstByDeviceCodeOrderBySeqDesc("SL-001"))
                .thenReturn(Optional.of(anchor(1L, hash1, hmac(1L, hash1, anchoredAt), anchoredAt)));
        when(entryRepository.findByDeviceCodeAndSeq("SL-001", 1L)).thenReturn(Optional.of(savedEntries.get(0)));

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_VALID_PARTIALLY_ANCHORED);
        assertThat(result.anchorCoverageComplete()).isFalse();
    }

    @Test
    void verifyReportsInvalidAnchorSignature() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        when(anchorRepository.findFirstByDeviceCodeOrderBySeqDesc("SL-001"))
                .thenReturn(Optional.of(anchor(1L, savedEntries.get(0).getEntryHash(), "0000", 1755835200000L)));

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.anchorState()).isEqualTo(EvidenceChainService.ANCHOR_SIGNATURE_INVALID);
    }

    @Test
    void verifyReportsInvalidAnchorChainMismatch() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        long anchoredAt = 1755835200000L;
        String wrongHash = "wrong";
        when(anchorRepository.findFirstByDeviceCodeOrderBySeqDesc("SL-001"))
                .thenReturn(Optional.of(anchor(1L, wrongHash, hmac(1L, wrongHash, anchoredAt), anchoredAt)));
        when(entryRepository.findByDeviceCodeAndSeq("SL-001", 1L)).thenReturn(Optional.of(savedEntries.get(0)));

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.anchorState()).isEqualTo(EvidenceChainService.ANCHOR_CHAIN_MISMATCH);
    }

    @Test
    void verifyReportsInvalidAnchorEntryMissing() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        long anchoredAt = 1755835200000L;
        String entryHash = savedEntries.get(0).getEntryHash();
        when(anchorRepository.findFirstByDeviceCodeOrderBySeqDesc("SL-001"))
                .thenReturn(Optional.of(anchor(1L, entryHash, hmac(1L, entryHash, anchoredAt), anchoredAt)));

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.anchorState()).isEqualTo(EvidenceChainService.ANCHOR_ENTRY_MISSING);
    }

    // ==================== anchorAll ====================

    @Test
    void anchorAllCreatesAnchorForValidTail() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        when(headRepository.findAll()).thenReturn(List.of(head));
        when(entryRepository.findByDeviceCodeAndSeq("SL-001", 1L)).thenReturn(Optional.of(savedEntries.get(0)));

        service.anchorAll();

        verify(anchorRepository).save(any(EvidenceAnchor.class));
    }

    @Test
    void anchorAllSkipsWhenSecretEmpty() {
        ReflectionTestUtils.setField(service, "hmacSecret", "");

        service.anchorAll();

        verify(anchorRepository, never()).save(any(EvidenceAnchor.class));
    }

    @Test
    void anchorAllSkipsWhenExistingAnchorInvalid() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        when(headRepository.findAll()).thenReturn(List.of(head));
        when(anchorRepository.findFirstByDeviceCodeOrderBySeqDesc("SL-001"))
                .thenReturn(Optional.of(anchor(1L, "hash", "bad", 0L)));

        service.anchorAll();

        verify(anchorRepository, never()).save(any(EvidenceAnchor.class));
    }

    @Test
    void anchorAllSkipsWhenTailHeadMismatch() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        EvidenceChainEntry tail = new EvidenceChainEntry();
        tail.setDeviceCode("SL-001");
        tail.setSeq(1L);
        tail.setEntryHash("different");
        tail.setEntryMac(savedEntries.get(0).getEntryMac());
        tail.setKeyId("v1");
        tail.setMacVersion(1);

        when(headRepository.findAll()).thenReturn(List.of(head));
        when(entryRepository.findByDeviceCodeAndSeq("SL-001", 1L)).thenReturn(Optional.of(tail));

        service.anchorAll();

        verify(anchorRepository, never()).save(any(EvidenceAnchor.class));
    }

    @Test
    void anchorAllSkipsWhenTailMacInvalid() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        savedEntries.get(0).setEntryMac("deadbeef");
        when(headRepository.findAll()).thenReturn(List.of(head));
        when(entryRepository.findByDeviceCodeAndSeq("SL-001", 1L)).thenReturn(Optional.of(savedEntries.get(0)));

        service.anchorAll();

        verify(anchorRepository, never()).save(any(EvidenceAnchor.class));
    }

    @Test
    void anchorAllSkipsWhenHeadMacInvalid() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);

        head.setHeadMac("deadbeef");
        when(headRepository.findAll()).thenReturn(List.of(head));

        service.anchorAll();

        verify(anchorRepository, never()).save(any(EvidenceAnchor.class));
    }

    // ==================== 分页 / 条件 ====================

    @Test
    void getEntriesSortsBySeqAscending() {
        when(entryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.getEntries("SL-001", 0, 50, null, null, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(entryRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("seq").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void verifyPaginatesLargeChain() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        for (int i = 0; i < 501; i++) {
            appendTelemetry(point);
        }

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_VALID_UNANCHORED);
        assertThat(result.checkedCount()).isEqualTo(501);
    }

    @Test
    void shouldCreateAnchorUsesCountOrTime() {
        long now = System.currentTimeMillis();
        long fiveMinAgo = now - 300000;

        assertThat(service.shouldCreateAnchor(200, 100, fiveMinAgo, now)).isTrue();
        assertThat(service.shouldCreateAnchor(199, 100, fiveMinAgo, now)).isTrue();
        assertThat(service.shouldCreateAnchor(199, 100, now, now)).isFalse();
    }

    @Test
    void constantTimeEqualsRejectsNull() {
        assertThat(service.constantTimeEquals(null, null)).isFalse();
        assertThat(service.constantTimeEquals("abc", null)).isFalse();
        assertThat(service.constantTimeEquals(null, "abc")).isFalse();
    }

    @Test
    void domainSeparationProducesDistinctMacs() {
        String entryMac = service.computeEntryMacV1("v1", "SL-001", 1L, "abc");
        String headMac = service.computeHeadMacV1("v1", "SL-001", 1L, "abc");

        assertThat(entryMac).isNotEqualTo(headMac);
    }

    // ==================== getChainMetadata ====================

    @Test
    void getChainMetadataReportsValidHeadMac() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        when(entryRepository.existsByDeviceCode("SL-001")).thenReturn(true);

        EvidenceChainService.ChainMetadata meta = service.getChainMetadata("SL-001");

        assertThat(meta.hasEvidence()).isTrue();
        assertThat(meta.headState()).isEqualTo(EvidenceChainService.HEAD_STATE_VALID);
        assertThat(meta.metadataState()).isEqualTo(EvidenceChainService.META_NORMAL);
        assertThat(meta.metadataIssue()).isNull();
    }

    @Test
    void getChainMetadataDetectsHeadSeqTamper() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        when(entryRepository.existsByDeviceCode("SL-001")).thenReturn(true);
        head.setLatestSeq(2L);

        EvidenceChainService.ChainMetadata meta = service.getChainMetadata("SL-001");

        assertThat(meta.headState()).isEqualTo(EvidenceChainService.HEAD_STATE_INVALID);
        assertThat(meta.metadataState()).isEqualTo(EvidenceChainService.META_INCONSISTENT);
        assertThat(meta.metadataIssue()).isEqualTo(EvidenceChainService.META_ISSUE_HEAD_MAC_INVALID);
    }

    @Test
    void getChainMetadataDetectsHeadMacTamper() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        when(entryRepository.existsByDeviceCode("SL-001")).thenReturn(true);
        head.setHeadMac("deadbeef");

        EvidenceChainService.ChainMetadata meta = service.getChainMetadata("SL-001");

        assertThat(meta.headState()).isEqualTo(EvidenceChainService.HEAD_STATE_INVALID);
        assertThat(meta.metadataState()).isEqualTo(EvidenceChainService.META_INCONSISTENT);
    }

    @Test
    void getChainMetadataReportsHeadMissing() {
        when(entryRepository.existsByDeviceCode("SL-001")).thenReturn(true);
        when(headRepository.findById("SL-001")).thenReturn(Optional.empty());

        EvidenceChainService.ChainMetadata meta = service.getChainMetadata("SL-001");

        assertThat(meta.hasEvidence()).isTrue();
        assertThat(meta.headState()).isEqualTo(EvidenceChainService.HEAD_STATE_NONE);
        assertThat(meta.metadataState()).isEqualTo(EvidenceChainService.META_INCONSISTENT);
        assertThat(meta.metadataIssue()).isEqualTo(EvidenceChainService.META_ISSUE_HEAD_MISSING);
    }

    @Test
    void getChainMetadataReportsAnchorAheadOfHead() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        when(entryRepository.existsByDeviceCode("SL-001")).thenReturn(true);

        long anchoredAt = 1755835200000L;
        String anchorHash = "a".repeat(64);
        when(anchorRepository.findFirstByDeviceCodeOrderBySeqDesc("SL-001"))
                .thenReturn(Optional.of(anchor(100L, anchorHash, hmac(100L, anchorHash, anchoredAt), anchoredAt)));
        EvidenceChainEntry entry100 = new EvidenceChainEntry();
        entry100.setSeq(100L);
        entry100.setEntryHash(anchorHash);
        when(entryRepository.findByDeviceCodeAndSeq("SL-001", 100L)).thenReturn(Optional.of(entry100));

        EvidenceChainService.ChainMetadata meta = service.getChainMetadata("SL-001");

        assertThat(meta.metadataState()).isEqualTo(EvidenceChainService.META_INCONSISTENT);
        assertThat(meta.metadataIssue()).isEqualTo(EvidenceChainService.META_ISSUE_ANCHOR_AHEAD_OF_HEAD);
        assertThat(meta.unanchoredCount()).isNull();
    }

    @Test
    void verifyReportsHasEvidenceWhenHeadMissing() {
        LightPoint point = point(85.0);
        when(lightPointRepository.findById(1L)).thenReturn(Optional.of(point));
        appendTelemetry(point);
        when(headRepository.findById("SL-001")).thenReturn(Optional.empty());

        EvidenceChainService.VerificationResult result = service.verify("SL-001");

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.overallStatus()).isEqualTo(EvidenceChainService.STATUS_INVALID_CHAIN);
        assertThat(result.breakType()).isEqualTo(EvidenceChainService.BREAK_HEAD_MISMATCH);
    }

    // ==================== 辅助 ====================

    private void appendTelemetry(LightPoint point) {
        service.append("SL-001", EvidenceChainService.EVENT_TELEMETRY, point.getTs(),
                EvidenceChainService.SOURCE_LIGHT_POINT, 1L,
                service.buildTelemetryPayload(point));
    }

    private LightPoint point(double lux) {
        LightPoint p = new LightPoint();
        p.setDeviceCode("SL-001");
        p.setTs(1755835200000L);
        p.setLux(lux);
        p.setTemperature(27.2);
        p.setVoltage(220.1);
        p.setCurrent(0.38);
        p.setPower(83.6);
        p.setEnergy(14.7);
        p.setLampStatus("OFF");
        return p;
    }

    private EvidenceAnchor anchor(long seq, String entryHash, String signature, long anchoredAt) {
        EvidenceAnchor a = new EvidenceAnchor();
        a.setDeviceCode("SL-001");
        a.setSeq(seq);
        a.setEntryHash(entryHash);
        a.setSignature(signature);
        a.setAnchoredAt(anchoredAt);
        return a;
    }

    /** 复刻 EvidenceChainService.computeAnchorSignature（secret=test-secret，含 domain）。 */
    private String hmac(long seq, String entryHash, long anchoredAt) {
        String input = "{\"domain\":\"ANCHOR\",\"deviceCode\":\"SL-001\",\"seq\":" + seq
                + ",\"entryHash\":\"" + entryHash + "\",\"anchoredAt\":" + anchoredAt + "}";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
