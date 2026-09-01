package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.BrightnessPeriodDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {
    @Mock private ConfigService configService;
    private ConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfigController();
        ReflectionTestUtils.setField(controller, "configService", configService);
    }

    @Test
    void zeroPercentIsRejectedSoScheduleCannotTurnLightsOff() {
        LinkageConfigDTO request = validRequest();
        request.setBrightnessPeriods(List.of(new BrightnessPeriodDTO("深夜", "00:00", 0)));

        ApiResponse<Void> response = controller.saveLinkageConfig(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).contains("1–100").contains("关灯策略");
        verify(configService, never()).saveLinkageConfig(any());
    }

    @Test
    void duplicateStartTimesAreRejected() {
        LinkageConfigDTO request = validRequest();
        request.setBrightnessPeriods(List.of(
                new BrightnessPeriodDTO("时段一", "23:00", 80),
                new BrightnessPeriodDTO("时段二", "23:00", 60)));

        ApiResponse<Void> response = controller.saveLinkageConfig(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).contains("不能重复");
        verify(configService, never()).saveLinkageConfig(any());
    }

    private LinkageConfigDTO validRequest() {
        LinkageConfigDTO request = new LinkageConfigDTO();
        request.setEnabled(true);
        request.setThreshold(100);
        request.setHysteresis(50);
        request.setBrightnessScheduleEnabled(true);
        return request;
    }
}
