package com.smartlamp.service;

import com.smartlamp.entity.SystemConfig;
import com.smartlamp.entity.Device;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.dto.SystemConfigDTO;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {
    @Mock private SystemConfigRepository repository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private MqttPublisherService mqttPublisherService;

    private ConfigService service;

    @BeforeEach
    void setUp() {
        service = new ConfigService();
        inject("repository", repository);
        inject("deviceRepository", deviceRepository);
        inject("mqttPublisherService", mqttPublisherService);
    }

    @Test
    void simulatorLinkageUsesThresholdAndPreservesStateInsideHysteresisBand() {
        SystemConfig config = new SystemConfig();
        config.setAutoControl(true);
        config.setLuxThreshold(50);
        config.setHysteresis(50);
        when(repository.findById(1L)).thenReturn(Optional.of(config));

        assertThat(service.resolveSimulatorLampStatus(49, "OFF", "OFF")).isEqualTo("ON");
        assertThat(service.resolveSimulatorLampStatus(101, "ON", "ON")).isEqualTo("OFF");
        assertThat(service.resolveSimulatorLampStatus(97, "OFF", "ON")).isEqualTo("OFF");
    }

    @Test
    void disablingGlobalLinkageMovesEveryDeviceToManualMode() {
        SystemConfig config = new SystemConfig();
        Device first = new Device();
        first.setCode("SIM-001");
        first.setControlMode("AUTO");
        Device second = new Device();
        second.setCode("SIM-002");
        second.setControlMode("AUTO");
        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(deviceRepository.findAll()).thenReturn(List.of(first, second));
        LinkageConfigDTO request = new LinkageConfigDTO();
        request.setEnabled(false);
        request.setThreshold(50);
        request.setHysteresis(50);

        service.saveLinkageConfig(request);

        assertThat(first.getControlMode()).isEqualTo("MANUAL");
        assertThat(second.getControlMode()).isEqualTo("MANUAL");
        verify(deviceRepository).save(first);
        verify(deviceRepository).save(second);
    }

    @Test
    void enablingGlobalLinkageMovesEveryDeviceToAutoMode() {
        SystemConfig config = new SystemConfig();
        Device first = new Device();
        first.setCode("SIM-001");
        first.setControlMode("MANUAL");
        Device second = new Device();
        second.setCode("SIM-002");
        second.setControlMode("MANUAL");
        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(deviceRepository.findAll()).thenReturn(List.of(first, second));
        LinkageConfigDTO request = new LinkageConfigDTO();
        request.setEnabled(true);
        request.setThreshold(50);
        request.setHysteresis(50);

        service.saveLinkageConfig(request);

        assertThat(first.getControlMode()).isEqualTo("AUTO");
        assertThat(second.getControlMode()).isEqualTo("AUTO");
    }

    @Test
    void timeScheduleControlsPercentageAndWrapsAcrossMidnight() {
        SystemConfig config = new SystemConfig();
        config.setBrightnessScheduleEnabled(true);
        config.setBrightnessSchedule("00:00|深夜|40;05:00|清晨|75;18:00|傍晚|100;23:00|夜间|60");
        when(repository.findById(1L)).thenReturn(Optional.of(config));

        assertThat(service.resolveBrightnessPercent(LocalTime.of(0, 30))).isEqualTo(40);
        assertThat(service.resolveBrightnessPercent(LocalTime.of(6, 0))).isEqualTo(75);
        assertThat(service.resolveBrightnessPercent(LocalTime.of(22, 0))).isEqualTo(100);
        assertThat(service.resolveBrightnessPercent(LocalTime.of(23, 30))).isEqualTo(60);
    }

    @Test
    void scheduleIsAutomaticallyReenabledBecauseItIsPermanent() {
        SystemConfig config = new SystemConfig();
        config.setBrightnessScheduleEnabled(false);
        config.setBrightnessSchedule("00:00|深夜|40");
        when(repository.findById(1L)).thenReturn(Optional.of(config));

        assertThat(service.resolveBrightnessPercent(LocalTime.of(2, 0))).isEqualTo(40);
        assertThat(config.isBrightnessScheduleEnabled()).isTrue();
        verify(repository).save(config);
    }

    @Test
    void brightnessCommandDoesNotChangeExistingOnOffState() {
        SystemConfig config = new SystemConfig();
        config.setBrightnessScheduleEnabled(true);
        config.setBrightnessSchedule("00:00|深夜|55");
        Device device = new Device();
        device.setCode("SIM-001");
        device.setBound(true);
        device.setLampStatus("ON");
        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(deviceRepository.findAll()).thenReturn(List.of(device));

        service.publishCurrentBrightness(LocalTime.of(2, 0), true);

        verify(mqttPublisherService).publish(eq("device/SIM-001/cmd"),
                contains("\"action\":\"SET_BRIGHTNESS\",\"brightness\":55"));
        assertThat(device.getLampStatus()).isEqualTo("ON");
    }

    @Test
    void legacySystemConfigSaveKeepsBrightnessScheduleAvailable() {
        SystemConfig config = new SystemConfig();
        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(deviceRepository.findAll()).thenReturn(List.of());
        SystemConfigDTO request = new SystemConfigDTO();
        request.setAutoControl(true);
        request.setLuxThreshold(80);
        request.setHysteresis(20);
        request.setHeartbeatTimeoutMs(30_000);

        service.saveConfig(request);

        assertThat(config.getBrightnessSchedule()).isNotBlank();
        assertThat(service.getLinkageConfig().getBrightnessPeriods()).isNotEmpty();
    }

    @Test
    void existingDatabaseRowWithoutNewColumnsIsMigratedToDefaultSchedule() {
        SystemConfig legacy = new SystemConfig();
        legacy.setBrightnessSchedule(null);
        legacy.setBrightnessScheduleEnabled(false);
        when(repository.findById(1L)).thenReturn(Optional.of(legacy));

        LinkageConfigDTO result = service.getLinkageConfig();

        assertThat(result.getBrightnessScheduleEnabled()).isTrue();
        assertThat(result.getBrightnessPeriods()).hasSize(5);
        verify(repository).save(legacy);
    }

    private void inject(String fieldName, Object value) {
        try {
            var field = ConfigService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
