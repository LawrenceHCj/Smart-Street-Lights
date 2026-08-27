package com.smartlamp.service;

import com.smartlamp.dto.DeviceHealthDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceHealthReport;
import com.smartlamp.repository.DeviceHealthReportRepository;
import com.smartlamp.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceHealthServiceTest {

    @Mock
    private DeviceHealthReportRepository healthReportRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceHealthService healthService;

    @Test
    void evaluatesAllRulesAndReturnsReadableAnomalies() {
        Device device = new Device();
        device.setCode("SL-001");
        device.setStatus("ONLINE");
        device.setLampStatus("OFF");
        device.setLatestPower(12.0);
        device.setLatestTemperature(70.0);
        device.setLatestCurrent(5.5);
        device.setLatestVoltage(190.0);
        device.setLatestLux(36.5);
        device.setLatestEnergy(12.8);
        device.setLastSeen(1_777_777_777_000L);
        when(deviceRepository.findByCode("SL-001")).thenReturn(Optional.of(device));
        when(healthReportRepository.save(any(DeviceHealthReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceHealthDTO report = healthService.evaluateDeviceHealth("SL-001");

        assertThat(report.healthScore()).isEqualTo(55);
        assertThat(report.telemetry().lux()).isEqualTo(36.5);
        assertThat(report.telemetry().voltage()).isEqualTo(190.0);
        assertThat(report.telemetry().collectedAt()).isEqualTo(1_777_777_777_000L);
        assertThat(report.anomalies()).hasSize(4);
        assertThat(report.anomalies()).extracting("deduct").containsExactly(15, 10, 10, 10);
    }

    @Test
    void returnsNullWhenDeviceDoesNotExist() {
        when(deviceRepository.findByCode("missing")).thenReturn(Optional.empty());

        assertThat(healthService.evaluateDeviceHealth("missing")).isNull();
    }

    @Test
    void doesNotEvaluateOfflineDevice() {
        Device device = new Device();
        device.setCode("SL-OFFLINE");
        device.setStatus("OFFLINE");
        when(deviceRepository.findByCode("SL-OFFLINE")).thenReturn(Optional.of(device));

        assertThat(healthService.evaluateDeviceHealth("SL-OFFLINE")).isNull();
    }

    @Test
    void returnsLatestReportsWithParsedDetails() {
        DeviceHealthReport entity = new DeviceHealthReport();
        entity.setId(8L);
        entity.setDeviceCode("SL-008");
        entity.setHealthScore(90);
        entity.setAnomalyDetails("[{\"issue\":\"过热预警\",\"reason\":\"温度偏高\",\"deduct\":10}]");
        entity.setTelemetrySnapshot("{\"lux\":28.5,\"temperature\":68.0,\"lampStatus\":\"ON\",\"collectedAt\":1777777777000}");
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 26, 10, 30));
        when(healthReportRepository.findLatestForAllDevices()).thenReturn(List.of(entity));

        List<DeviceHealthDTO> reports = healthService.getLatestReports();

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).deviceCode()).isEqualTo("SL-008");
        assertThat(reports.get(0).telemetry().temperature()).isEqualTo(68.0);
        assertThat(reports.get(0).anomalies()).singleElement()
                .satisfies(anomaly -> assertThat(anomaly.issue()).isEqualTo("过热预警"));
    }
}
