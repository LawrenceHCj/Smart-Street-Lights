package com.smartlamp.service;

import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCommandServiceTest {
    @Mock private DeviceCommandRepository commandRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private MqttPublisherService mqttPublisherService;
    @Mock private DataIntegrityService dataIntegrityService;

    private DeviceCommandService service;

    @BeforeEach
    void setUp() {
        service = new DeviceCommandService(commandRepository, deviceRepository, mqttPublisherService,
                dataIntegrityService);
    }

    @Test
    void managedSimulatorCompletesCommandAndPersistsLampState() {
        Device device = onlineBoundDevice("SIM-HUXI-001");
        when(deviceRepository.findByCode(device.getCode())).thenReturn(Optional.of(device));
        when(commandRepository.save(any(DeviceCommand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCommand command = service.dispatch(device.getCode(), "OFF", "MANUAL");

        assertThat(command.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(command.getMode()).isEqualTo("MANUAL");
        assertThat(device.getLampStatus()).isEqualTo("OFF");
        verify(mqttPublisherService).publish(contains("SIM-HUXI-001"), contains("\"action\":\"OFF\""));
        verify(deviceRepository).save(device);
        verify(dataIntegrityService).appendCommand(command, DataIntegrityService.EVENT_COMMAND_DISPATCHED);
        verify(dataIntegrityService).appendCommand(command, DataIntegrityService.EVENT_COMMAND_SUCCESS);
    }

    @Test
    void realDeviceStillWaitsForHardwareAcknowledgement() {
        Device device = onlineBoundDevice("SL-001");
        when(deviceRepository.findByCode(device.getCode())).thenReturn(Optional.of(device));
        when(commandRepository.save(any(DeviceCommand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCommand command = service.dispatch(device.getCode(), "ON", "MANUAL");

        assertThat(command.getStatus()).isEqualTo(CommandStatus.DISPATCHED);
        assertThat(device.getLampStatus()).isEqualTo("OFF");
    }

    @Test
    void simulatorTelemetryUsesMostRecentSuccessfulCommand() {
        DeviceCommand command = new DeviceCommand();
        command.setAction("OFF");
        when(commandRepository.findFirstByDeviceCodeAndStatusOrderByUpdatedAtDesc(
                "SIM-HUXI-001", CommandStatus.SUCCESS)).thenReturn(Optional.of(command));

        assertThat(service.resolveReportedLampStatus("SIM-HUXI-001", "ON")).isEqualTo("OFF");
        assertThat(service.resolveReportedLampStatus("SL-001", "ON")).isEqualTo("ON");
    }

    @Test
    void timedOutCommandIsAlsoWrittenToIntegrityLog() {
        DeviceCommand command = new DeviceCommand();
        command.setId(9L);
        command.setCommandId("cmd-9");
        command.setDeviceCode("SL-001");
        command.setAction("ON");
        command.setMode("AGENT");
        command.setStatus(CommandStatus.DISPATCHED);
        command.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        when(commandRepository.findByStatusAndCreatedAtBefore(
                org.mockito.ArgumentMatchers.eq(CommandStatus.DISPATCHED), any(LocalDateTime.class)))
                .thenReturn(List.of(command));
        when(commandRepository.findByStatusAndCreatedAtBefore(
                org.mockito.ArgumentMatchers.eq(CommandStatus.ACKED), any(LocalDateTime.class)))
                .thenReturn(List.of());

        service.markTimedOutCommands();

        assertThat(command.getStatus()).isEqualTo(CommandStatus.TIMEOUT);
        verify(dataIntegrityService).appendCommand(command, DataIntegrityService.EVENT_COMMAND_TIMEOUT);
        verify(mqttPublisherService, never()).publish(any(), any());
    }

    private Device onlineBoundDevice(String code) {
        Device device = new Device();
        device.setCode(code);
        device.setStatus("ONLINE");
        device.setBound(true);
        device.setLampStatus("OFF");
        return device;
    }
}
