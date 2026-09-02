package com.smartlamp.service;

import com.smartlamp.entity.DataIntegrityEntry;
import com.smartlamp.entity.DataIntegrityHead;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.DataIntegrityEntryRepository;
import com.smartlamp.repository.DataIntegrityHeadRepository;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.LightPointRepository;
import com.smartlamp.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataIntegrityServiceTest {
    @Mock private DataIntegrityEntryRepository entryRepository;
    @Mock private DataIntegrityHeadRepository headRepository;
    @Mock private LightPointRepository lightPointRepository;
    @Mock private DeviceCommandRepository commandRepository;
    @Mock private JwtUtil jwtUtil;

    private DataIntegrityService service;
    private DataIntegrityHead head;
    private LightPoint point;

    @BeforeEach
    void setUp() {
        when(jwtUtil.deriveSubkey("data-integrity-v1"))
                .thenReturn("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        service = new DataIntegrityService(entryRepository, headRepository, lightPointRepository,
                commandRepository, new ObjectMapper(), jwtUtil);

        head = new DataIntegrityHead();
        head.setDeviceCode("SL-001");
        head.setLatestSequence(0L);
        head.setLatestMac("");
        head.setKeyVersion(1);
        when(headRepository.findForUpdate("SL-001")).thenReturn(Optional.of(head));
        when(entryRepository.save(any(DataIntegrityEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        point = new LightPoint();
        point.setId(42L);
        point.setDeviceCode("SL-001");
        point.setTs(1_755_835_200_000L);
        point.setLux(320.5);
        point.setTemperature(27.2);
        point.setVoltage(220.1);
        point.setCurrent(0.38);
        point.setPower(83.6);
        point.setEnergy(14.7);
        point.setLampStatus("ON");
        point.setRawPayload("{\"deviceId\":\"SL-001\",\"lux\":320.5}");
    }

    @Test
    void appendTelemetryCreatesAuthenticatedChainAndHead() {
        DataIntegrityEntry entry = service.appendTelemetry(point);

        assertThat(entry.getSequenceNo()).isEqualTo(1L);
        assertThat(entry.getPreviousMac()).isEmpty();
        assertThat(entry.getEventDigest()).hasSize(64);
        assertThat(entry.getSourceDigest()).hasSize(64);
        assertThat(entry.getChainMac()).hasSize(64);
        assertThat(head.getLatestSequence()).isEqualTo(1L);
        assertThat(head.getLatestMac()).isEqualTo(entry.getChainMac());
        assertThat(head.getHeadMac()).hasSize(64);
        verify(headRepository).save(head);
    }

    @Test
    void verifyDetectsBusinessSourceTamperingWhileSourceIsRetained() {
        DataIntegrityEntry entry = service.appendTelemetry(point);
        prepareVerification(entry);
        when(lightPointRepository.findById(42L)).thenReturn(Optional.of(point));

        DataIntegrityService.VerificationResult valid = service.verify("SL-001");
        assertThat(valid.valid()).isTrue();
        assertThat(valid.checkedEntries()).isEqualTo(1);
        assertThat(valid.checkedSources()).isEqualTo(1);

        point.setLux(999.0);
        DataIntegrityService.VerificationResult tampered = service.verify("SL-001");
        assertThat(tampered.valid()).isFalse();
        assertThat(tampered.issue()).isEqualTo(DataIntegrityService.ISSUE_SOURCE);
        assertThat(tampered.firstBrokenSequence()).isEqualTo(1L);
    }

    @Test
    void verifyDetectsAuthenticatedLogTampering() {
        DataIntegrityEntry entry = service.appendTelemetry(point);
        prepareVerification(entry);
        entry.setEventDigest("0".repeat(64));

        DataIntegrityService.VerificationResult result = service.verify("SL-001");

        assertThat(result.valid()).isFalse();
        assertThat(result.issue()).isEqualTo(DataIntegrityService.ISSUE_ENTRY_MAC);
        assertThat(result.firstBrokenSequence()).isEqualTo(1L);
    }

    @Test
    void verifyAllowsExpiredSourceToBeRemovedWithoutBreakingCryptographicChain() {
        DataIntegrityEntry entry = service.appendTelemetry(point);
        prepareVerification(entry);
        when(lightPointRepository.findById(42L)).thenReturn(Optional.empty());

        DataIntegrityService.VerificationResult result = service.verify("SL-001");

        assertThat(result.valid()).isTrue();
        assertThat(result.checkedSources()).isZero();
    }

    @Test
    void verifyRejectsHeadResetToEmptyAfterLogDeletion() {
        service.appendTelemetry(point);
        head.setLatestSequence(0L);
        head.setLatestMac("");
        head.setHeadMac(null);
        when(headRepository.findById("SL-001")).thenReturn(Optional.of(head));
        when(entryRepository.existsByDeviceCode("SL-001")).thenReturn(false);

        DataIntegrityService.VerificationResult result = service.verify("SL-001");

        assertThat(result.valid()).isFalse();
        assertThat(result.issue()).isEqualTo(DataIntegrityService.ISSUE_HEAD_MAC);
    }

    private void prepareVerification(DataIntegrityEntry entry) {
        when(headRepository.findById("SL-001")).thenReturn(Optional.of(head));
        when(entryRepository.existsByDeviceCode("SL-001")).thenReturn(true);
        when(entryRepository.findByDeviceCodeAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                anyString(), anyLong(), any())).thenReturn(List.of(entry));
    }
}
