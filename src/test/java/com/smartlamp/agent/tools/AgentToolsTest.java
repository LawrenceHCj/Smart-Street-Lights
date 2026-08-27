package com.smartlamp.agent.tools;

import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.LightHistoryDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Alarm;
import com.smartlamp.exception.BadRequestException;
import com.smartlamp.service.AlarmService;
import com.smartlamp.service.ConfigService;
import com.smartlamp.service.DeviceService;
import com.smartlamp.service.LightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 逐个测试只读 Tool：参数校验、正常返回、空数据处理；mock 3号 Service，不依赖 MySQL/MQTT
@ExtendWith(MockitoExtension.class)
class AgentToolsTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private LightService lightService;
    @Mock
    private AlarmService alarmService;
    @Mock
    private ConfigService configService;

    private AgentTools agentTools;

    @BeforeEach
    void setUp() {
        agentTools = new AgentTools();
        ReflectionTestUtils.setField(agentTools, "deviceService", deviceService);
        ReflectionTestUtils.setField(agentTools, "lightService", lightService);
        ReflectionTestUtils.setField(agentTools, "alarmService", alarmService);
        ReflectionTestUtils.setField(agentTools, "configService", configService);
    }

    // ============ getDeviceList ============

    @Test
    void getDeviceList返回设备列表() {
        when(deviceService.getAllDeviceDTOs()).thenReturn(List.of(
                new DeviceDTO(1L, "SL-001", "北门", "ONLINE", 120.0, 1700000000000L)));

        List<DeviceDTO> result = agentTools.getDeviceList();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("SL-001");
    }

    @Test
    void getDeviceList服务返回null时返回空列表() {
        when(deviceService.getAllDeviceDTOs()).thenReturn(null);

        assertThat(agentTools.getDeviceList()).isEmpty();
    }

    // ============ getDeviceStatus ============

    @Test
    void getDeviceStatus设备存在时返回状态() {
        when(deviceService.getAllDeviceDTOs()).thenReturn(List.of(
                new DeviceDTO(1L, "SL-001", "北门", "ONLINE", 120.0, 1700000000000L)));

        DeviceDTO result = agentTools.getDeviceStatus("SL-001");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ONLINE");
    }

    @Test
    void getDeviceStatus设备不存在时返回null() {
        when(deviceService.getAllDeviceDTOs()).thenReturn(List.of(
                new DeviceDTO(1L, "SL-001", "北门", "ONLINE", 120.0, 1700000000000L)));

        assertThat(agentTools.getDeviceStatus("SL-999")).isNull();
    }

    @Test
    void getDeviceStatus参数为空时抛出400() {
        assertThatThrownBy(() -> agentTools.getDeviceStatus(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deviceCode");
        assertThatThrownBy(() -> agentTools.getDeviceStatus("  "))
                .isInstanceOf(BadRequestException.class);
    }

    // ============ getLatestTelemetry ============

    @Test
    void getLatestTelemetry返回最新光照() {
        when(deviceService.getCurrentLight("SL-001"))
                .thenReturn(new LightDataDTO("SL-001", 82.5, 1700000000000L));

        LightDataDTO result = agentTools.getLatestTelemetry("SL-001");

        assertThat(result.getDeviceId()).isEqualTo("SL-001");
        assertThat(result.getLux()).isEqualTo(82.5);
    }

    @Test
    void getLatestTelemetry设备无数据时返回null() {
        when(deviceService.getCurrentLight("SL-001")).thenReturn(null);

        assertThat(agentTools.getLatestTelemetry("SL-001")).isNull();
    }

    @Test
    void getLatestTelemetry参数为空时抛出400() {
        assertThatThrownBy(() -> agentTools.getLatestTelemetry(""))
                .isInstanceOf(BadRequestException.class);
    }

    // ============ getTelemetryHistory ============

    @Test
    void getTelemetryHistory返回历史曲线() {
        LightHistoryDTO history = new LightHistoryDTO("SL-001",
                List.of(new LightHistoryDTO.Point(1700000000000L, 80.0), new LightHistoryDTO.Point(1700000001000L, 90.0)));
        when(lightService.getHistory("SL-001", 1700000000000L, 1700000001000L)).thenReturn(history);

        LightHistoryDTO result = agentTools.getTelemetryHistory(" SL-001 ", 1700000000000L, 1700000001000L);

        assertThat(result.getDeviceId()).isEqualTo("SL-001");
        assertThat(result.getPoints()).hasSize(2);
        verify(lightService).getHistory("SL-001", 1700000000000L, 1700000001000L);
    }

    @Test
    void getTelemetryHistory时间参数非法时抛出400() {
        assertThatThrownBy(() -> agentTools.getTelemetryHistory("SL-001", null, 1000L))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> agentTools.getTelemetryHistory("SL-001", 2000L, 1000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("start");
    }

    @Test
    void getTelemetryHistory设备编号为空时抛出400() {
        assertThatThrownBy(() -> agentTools.getTelemetryHistory(null, 1L, 2L))
                .isInstanceOf(BadRequestException.class);
    }

    // ============ getAlertHistory ============

    @Test
    void getAlertHistory返回告警列表() {
        Alarm alarm = new Alarm();
        alarm.setId(1L);
        alarm.setDeviceId("SL-001");
        alarm.setType("离线");
        alarm.setStatus("OPEN");
        when(alarmService.getAllAlarms()).thenReturn(List.of(alarm));

        List<Alarm> result = agentTools.getAlertHistory();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("离线");
    }

    @Test
    void getAlertHistory服务返回null时返回空列表() {
        when(alarmService.getAllAlarms()).thenReturn(null);

        assertThat(agentTools.getAlertHistory()).isEmpty();
    }

    // ============ getLinkageConfig ============

    @Test
    void getLinkageConfig返回真实配置() {
        LinkageConfigDTO config = new LinkageConfigDTO();
        config.setEnabled(true);
        config.setThreshold(30);
        when(configService.getLinkageConfig()).thenReturn(config);

        LinkageConfigDTO result = agentTools.getLinkageConfig();

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getThreshold()).isEqualTo(30);
    }
}
