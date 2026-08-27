package com.smartlamp.task;

import com.smartlamp.dto.SystemConfigDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.service.AlarmService;
import com.smartlamp.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceOfflineTaskTest {
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private AlarmService alarmService;
    @Mock
    private ConfigService configService;
    @InjectMocks
    private DeviceOfflineTask task;

    @Test
    void usesConfiguredHeartbeatTimeoutAndOnlyMarksStaleOnlineDevices() {
        long now = System.currentTimeMillis();
        Device stale = device("SL-STALE", "ONLINE", now - 60_000);
        Device fresh = device("SL-FRESH", "ONLINE", now - 5_000);
        Device offline = device("SL-OFFLINE", "OFFLINE", now - 60_000);
        SystemConfigDTO config = new SystemConfigDTO();
        config.setHeartbeatTimeoutMs(30_000);
        when(configService.getConfig()).thenReturn(config);
        when(deviceRepository.findAll()).thenReturn(List.of(stale, fresh, offline));

        task.checkOfflineDevices();

        assertThat(stale.getStatus()).isEqualTo("OFFLINE");
        assertThat(fresh.getStatus()).isEqualTo("ONLINE");
        verify(deviceRepository).save(stale);
        verify(deviceRepository, times(1)).save(any(Device.class));
        verify(alarmService).createOfflineAlarm(stale);
        verifyNoMoreInteractions(alarmService);
    }

    private Device device(String code, String status, long lastSeen) {
        Device device = new Device();
        device.setCode(code);
        device.setStatus(status);
        device.setLastSeen(lastSeen);
        return device;
    }
}
