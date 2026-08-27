package com.smartlamp.service;

import com.smartlamp.entity.Device;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.LightPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttIngestionServiceTest {
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private LightPointRepository lightPointRepository;
    @Mock
    private DeviceCommandService commandService;
    @Mock
    private AlarmService alarmService;

    private MqttIngestionService service;

    @BeforeEach
    void setUp() {
        service = new MqttIngestionService(deviceRepository, lightPointRepository, new ObjectMapper(),
                commandService, alarmService);
    }

    @Test
    void persistsStructuredTelemetryAndRawPayload() throws Exception {
        long now = System.currentTimeMillis();
        when(deviceRepository.findByCode("SL-001")).thenReturn(Optional.empty());
        when(lightPointRepository.existsByDeviceCodeAndTs("SL-001", now)).thenReturn(false);
        String payload = """
                {"deviceId":"SL-001","lux":320.5,"temperature":27.2,"voltage":220.1,
                 "current":0.38,"power":83.6,"energy":14.7,"lampStatus":"ON","ts":1755835200000,
                 "vendorField":"kept in raw payload"}
                """.replace("1755835200000", Long.toString(now));

        service.ingest("device/SL-001/data", payload);

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(deviceCaptor.capture());
        Device device = deviceCaptor.getValue();
        assertThat(device.getCode()).isEqualTo("SL-001");
        assertThat(device.getLatestLux()).isEqualTo(320.5);
        assertThat(device.getLatestTemperature()).isEqualTo(27.2);
        assertThat(device.getLatestVoltage()).isEqualTo(220.1);
        assertThat(device.getLampStatus()).isEqualTo("ON");
        assertThat(device.getStatus()).isEqualTo("ONLINE");
        assertThat(device.getLastTelemetryAt()).isEqualTo(now);

        ArgumentCaptor<LightPoint> pointCaptor = ArgumentCaptor.forClass(LightPoint.class);
        verify(lightPointRepository).save(pointCaptor.capture());
        LightPoint point = pointCaptor.getValue();
        assertThat(point.getPower()).isEqualTo(83.6);
        assertThat(point.getEnergy()).isEqualTo(14.7);
        assertThat(point.getRawPayload()).contains("vendorField");
        assertThat(point.getServerReceivedAt()).isNotNull();
    }

    @Test
    void heartbeatUpdatesSnapshotWithoutHistoryPoint() throws Exception {
        long now = System.currentTimeMillis();
        Device device = new Device();
        device.setCode("SL-001");
        when(deviceRepository.findByCode("SL-001")).thenReturn(Optional.of(device));

        service.ingest("device/SL-001/heartbeat", "{\"ts\":" + now + "}");

        assertThat(device.getLastSeen()).isEqualTo(now);
        assertThat(device.getStatus()).isEqualTo("ONLINE");
        verify(deviceRepository).save(device);
        verifyNoInteractions(lightPointRepository);
    }

    @Test
    void duplicateQosMessageDoesNotCreateAnotherHistoryPoint() throws Exception {
        long now = System.currentTimeMillis();
        Device device = new Device();
        device.setCode("SL-001");
        when(deviceRepository.findByCode("SL-001")).thenReturn(Optional.of(device));
        when(lightPointRepository.existsByDeviceCodeAndTs("SL-001", now)).thenReturn(true);

        service.ingest("device/SL-001/data", "{\"lux\":80,\"ts\":" + now + "}");

        verify(deviceRepository).save(device);
        verify(lightPointRepository, never()).save(any());
    }

    @Test
    void ignoresTelemetryAndHeartbeatOutsideClockWindow() throws Exception {
        long now = System.currentTimeMillis();
        for (String type : new String[]{"data", "heartbeat"}) {
            for (long ts : new long[]{now - 600_000, now + 600_000}) {
                service.ingest("device/SL-001/" + type, "{\"lux\":80,\"ts\":" + ts + "}");
            }
        }
        verifyNoInteractions(deviceRepository, lightPointRepository, alarmService);
    }

    @Test
    void ignoresOlderTelemetryAndHeartbeatWithoutRecoveringOfflineAlarm() throws Exception {
        long now = System.currentTimeMillis();
        Device device = new Device();
        device.setCode("SL-001");
        device.setStatus("OFFLINE");
        device.setLastSeen(now);
        device.setLastTelemetryAt(now);
        when(deviceRepository.findByCode("SL-001")).thenReturn(Optional.of(device));
        for (String type : new String[]{"data", "heartbeat"}) {
            service.ingest("device/SL-001/" + type, "{\"lux\":80,\"ts\":" + (now - 1000) + "}");
        }
        verify(deviceRepository, never()).save(any());
        verifyNoInteractions(lightPointRepository, alarmService);
        assertThat(device.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    void telemetryDoesNotMoveLastSeenBehindNewerHeartbeat() throws Exception {
        long now = System.currentTimeMillis();
        Device device = new Device();
        device.setCode("SL-001");
        device.setLastSeen(now);
        when(deviceRepository.findByCode("SL-001")).thenReturn(Optional.of(device));
        service.ingest("device/SL-001/data", "{\"lux\":80,\"ts\":" + (now - 1000) + "}");
        assertThat(device.getLastSeen()).isEqualTo(now);
        assertThat(device.getLastTelemetryAt()).isEqualTo(now - 1000);
        verify(lightPointRepository).save(any());
    }

    @Test
    void rejectsMismatchedDeviceIdBeforeWriting() {
        assertThatThrownBy(() -> service.ingest(
                "device/SL-001/data", "{\"deviceId\":\"SL-002\",\"lux\":80}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不一致");
        verifyNoInteractions(deviceRepository, lightPointRepository);
    }

    @Test
    void rejectsMissingOrNonNumericLuxBeforeWriting() {
        when(deviceRepository.findByCode("SL-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingest("device/SL-001/data", "{\"lux\":\"bright\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lux 必须是数值");
        verify(deviceRepository, never()).save(any());
        verify(lightPointRepository, never()).save(any());
    }

    @Test
    void routesCommandAcknowledgementWithoutCreatingTelemetry() throws Exception {
        service.ingest("device/SL-001/cmd_ack", "{\"commandId\":\"cmd-1\",\"status\":\"SUCCESS\"}");

        verify(commandService).acknowledge(eq("SL-001"), any());
        verifyNoInteractions(deviceRepository, lightPointRepository);
    }
}
