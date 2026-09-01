package com.smartlamp.service;

import com.smartlamp.dto.BrightnessPeriodDTO;
import com.smartlamp.dto.EnergySavingsDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.repository.LightPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergySavingsServiceTest {
    @Mock private LightPointRepository lightPointRepository;
    @Mock private ConfigService configService;
    private EnergySavingsService service;

    @BeforeEach
    void setUp() {
        service = new EnergySavingsService(lightPointRepository, configService);
        ReflectionTestUtils.setField(service, "electricityPrice", 0.60);
        ReflectionTestUtils.setField(service, "carbonFactor", 0.57);
    }

    @Test
    void estimatesSavingsAgainstFullPowerBaseline() {
        long bucketTs = System.currentTimeMillis() / 300_000 * 300_000;
        LightPointRepository.EnergySampleBucket bucket = mock(LightPointRepository.EnergySampleBucket.class);
        when(bucket.getDeviceCode()).thenReturn("SIM-001");
        when(bucket.getBucketTs()).thenReturn(bucketTs);
        when(bucket.getAverageOnPower()).thenReturn(100.0);
        when(bucket.getFirstTs()).thenReturn(bucketTs);
        when(bucket.getLastTs()).thenReturn(bucketTs + 295_000);
        when(bucket.getSampleCount()).thenReturn(60L);
        when(lightPointRepository.findEnergyBuckets(any(Long.class), any(Long.class))).thenReturn(List.of(bucket));

        LinkageConfigDTO config = new LinkageConfigDTO();
        List<BrightnessPeriodDTO> schedule = List.of(new BrightnessPeriodDTO("深夜节能", "00:00", 50));
        config.setBrightnessPeriods(schedule);
        when(configService.getLinkageConfig()).thenReturn(config);
        when(configService.resolveBrightnessPeriod(any(), any(LocalTime.class))).thenReturn(schedule.get(0));

        EnergySavingsDTO result = service.analyze(1);

        assertThat(result.getBaselineEnergyKwh()).isEqualTo(0.0083);
        assertThat(result.getEstimatedEnergyKwh()).isEqualTo(0.0042);
        assertThat(result.getSavedEnergyKwh()).isEqualTo(0.0042);
        assertThat(result.getSavingRatePercent()).isEqualTo(50.0);
        assertThat(result.getAverageBrightnessPercent()).isEqualTo(50.0);
        assertThat(result.getCoveredDeviceCount()).isEqualTo(1);
        assertThat(result.getDevices()).singleElement().satisfies(device ->
                assertThat(device.getDeviceId()).isEqualTo("SIM-001"));
    }

    @Test
    void usesPeakStablePowerAsReferenceInsteadOfScalingDimmedPowerTwice() {
        long firstTs = System.currentTimeMillis() / 300_000 * 300_000;
        LightPointRepository.EnergySampleBucket fullPower = stableBucket("SIM-001", firstTs, 100.0);
        LightPointRepository.EnergySampleBucket dimmedPower = stableBucket("SIM-001", firstTs + 300_000, 50.0);
        when(lightPointRepository.findEnergyBuckets(any(Long.class), any(Long.class)))
                .thenReturn(List.of(fullPower, dimmedPower));

        LinkageConfigDTO config = new LinkageConfigDTO();
        List<BrightnessPeriodDTO> schedule = List.of(new BrightnessPeriodDTO("深夜节能", "00:00", 50));
        config.setBrightnessPeriods(schedule);
        when(configService.getLinkageConfig()).thenReturn(config);
        when(configService.resolveBrightnessPeriod(any(), any(LocalTime.class))).thenReturn(schedule.get(0));

        EnergySavingsDTO result = service.analyze(1);

        assertThat(result.getBaselineEnergyKwh()).isEqualTo(0.0167);
        assertThat(result.getEstimatedEnergyKwh()).isEqualTo(0.0083);
        assertThat(result.getSavedEnergyKwh()).isEqualTo(0.0083);
    }

    @Test
    void singleSparseSampleIsNotExtrapolatedIntoEnergy() {
        LightPointRepository.EnergySampleBucket bucket = mock(LightPointRepository.EnergySampleBucket.class);
        when(bucket.getSampleCount()).thenReturn(1L);
        when(lightPointRepository.findEnergyBuckets(any(Long.class), any(Long.class))).thenReturn(List.of(bucket));
        LinkageConfigDTO config = new LinkageConfigDTO();
        config.setBrightnessPeriods(List.of(new BrightnessPeriodDTO("常规", "00:00", 100)));
        when(configService.getLinkageConfig()).thenReturn(config);

        EnergySavingsDTO result = service.analyze(7);

        assertThat(result.getSavedEnergyKwh()).isZero();
        assertThat(result.getSampleBucketCount()).isZero();
        assertThat(result.getCalculationMethod()).isEqualTo("ESTIMATED_FROM_REFERENCE_POWER_AND_SCHEDULE");
    }

    private LightPointRepository.EnergySampleBucket stableBucket(String deviceId, long ts, double power) {
        LightPointRepository.EnergySampleBucket bucket = mock(LightPointRepository.EnergySampleBucket.class);
        when(bucket.getDeviceCode()).thenReturn(deviceId);
        when(bucket.getBucketTs()).thenReturn(ts);
        when(bucket.getAverageOnPower()).thenReturn(power);
        when(bucket.getFirstTs()).thenReturn(ts);
        when(bucket.getLastTs()).thenReturn(ts + 295_000);
        when(bucket.getSampleCount()).thenReturn(60L);
        return bucket;
    }
}
