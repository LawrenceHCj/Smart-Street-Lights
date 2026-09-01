package com.smartlamp.service;

import com.smartlamp.dto.BrightnessPeriodDTO;
import com.smartlamp.dto.EnergySavingsDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.repository.LightPointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class EnergySavingsService {
    private static final ZoneId CONTROL_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long BUCKET_MS = 5L * 60 * 1000;
    private static final long MAX_AVERAGE_SAMPLE_INTERVAL_MS = 90_000;

    private final LightPointRepository lightPointRepository;
    private final ConfigService configService;

    @Value("${energy-savings.electricity-price-yuan-per-kwh:0.60}")
    private double electricityPrice;

    @Value("${energy-savings.carbon-factor-kg-per-kwh:0.57}")
    private double carbonFactor;

    public EnergySavingsService(LightPointRepository lightPointRepository, ConfigService configService) {
        this.lightPointRepository = lightPointRepository;
        this.configService = configService;
    }

    public EnergySavingsDTO analyze(int days) {
        long end = System.currentTimeMillis();
        long start = end - days * 24L * 60 * 60 * 1000;
        List<LightPointRepository.EnergySampleBucket> buckets = lightPointRepository.findEnergyBuckets(start, end);
        LinkageConfigDTO config = configService.getLinkageConfig();
        List<BrightnessPeriodDTO> schedule = config.getBrightnessPeriods();
        // 每台设备取统计期内最高稳定亮灯功率作为 100% 参考功率，避免对已调光功率再次乘比例。
        Map<String, Double> referencePowerByDevice = new HashMap<>();
        for (LightPointRepository.EnergySampleBucket bucket : buckets) {
            if (coverageHours(bucket) <= 0 || bucket.getAverageOnPower() == null) continue;
            referencePowerByDevice.merge(bucket.getDeviceCode(), bucket.getAverageOnPower(), Math::max);
        }

        Accumulator total = new Accumulator();
        Map<String, Accumulator> byDevice = new HashMap<>();
        Map<String, PeriodAccumulator> byPeriod = new LinkedHashMap<>();
        for (BrightnessPeriodDTO period : schedule) {
            byPeriod.put(period.getName(), new PeriodAccumulator(period.getName(), period.getBrightnessPercent()));
        }
        Map<Long, Accumulator> byTrend = new TreeMap<>();
        Set<String> coveredDevices = new HashSet<>();
        long acceptedBuckets = 0;

        for (LightPointRepository.EnergySampleBucket bucket : buckets) {
            double hours = coverageHours(bucket);
            Double referencePower = referencePowerByDevice.get(bucket.getDeviceCode());
            if (hours <= 0 || referencePower == null || referencePower <= 0) continue;

            long bucketTs = bucket.getBucketTs();
            LocalTime localTime = Instant.ofEpochMilli(bucketTs).atZone(CONTROL_ZONE).toLocalTime();
            BrightnessPeriodDTO period = configService.resolveBrightnessPeriod(schedule, localTime);
            double baseline = referencePower * hours / 1000.0;
            double estimated = baseline * period.getBrightnessPercent() / 100.0;
            double saved = Math.max(0, baseline - estimated);

            total.add(baseline, estimated, saved, hours);
            byDevice.computeIfAbsent(bucket.getDeviceCode(), ignored -> new Accumulator())
                    .add(baseline, estimated, saved, hours);
            byPeriod.computeIfAbsent(period.getName(),
                            ignored -> new PeriodAccumulator(period.getName(), period.getBrightnessPercent()))
                    .add(baseline, estimated, saved, hours);
            byTrend.computeIfAbsent(trendKey(bucketTs, days), ignored -> new Accumulator())
                    .add(baseline, estimated, saved, hours);
            coveredDevices.add(bucket.getDeviceCode());
            acceptedBuckets++;
        }

        List<EnergySavingsDTO.TrendPoint> trend = byTrend.entrySet().stream()
                .map(entry -> new EnergySavingsDTO.TrendPoint(entry.getKey(),
                        round(entry.getValue().baseline), round(entry.getValue().estimated),
                        round(entry.getValue().saved)))
                .toList();
        List<EnergySavingsDTO.DeviceSaving> devices = byDevice.entrySet().stream()
                .map(entry -> new EnergySavingsDTO.DeviceSaving(entry.getKey(),
                        round(entry.getValue().baseline), round(entry.getValue().estimated),
                        round(entry.getValue().saved), round(rate(entry.getValue())),
                        round(entry.getValue().hours)))
                .sorted(Comparator.comparingDouble(EnergySavingsDTO.DeviceSaving::getSavedEnergyKwh).reversed())
                .toList();
        List<EnergySavingsDTO.PeriodSaving> periods = byPeriod.values().stream()
                .map(period -> new EnergySavingsDTO.PeriodSaving(period.name, period.brightnessPercent,
                        round(period.baseline), round(period.estimated), round(period.saved), round(period.hours)))
                .toList();

        return new EnergySavingsDTO(
                end, start, end, days, coveredDevices.size(), acceptedBuckets, round(total.hours),
                round(total.baseline), round(total.estimated), round(total.saved), round(rate(total)),
                round(total.baseline <= 0 ? 100 : total.estimated / total.baseline * 100),
                round(total.saved * electricityPrice), round(total.saved * carbonFactor),
                electricityPrice, carbonFactor,
                "ESTIMATED_FROM_REFERENCE_POWER_AND_SCHEDULE", trend, devices, periods);
    }

    private double coverageHours(LightPointRepository.EnergySampleBucket bucket) {
        long count = bucket.getSampleCount() == null ? 0 : bucket.getSampleCount();
        if (count < 2 || bucket.getFirstTs() == null || bucket.getLastTs() == null) return 0;
        long observed = Math.max(0, bucket.getLastTs() - bucket.getFirstTs());
        double averageInterval = observed / (double) (count - 1);
        if (averageInterval > MAX_AVERAGE_SAMPLE_INTERVAL_MS) return 0;
        long estimatedCoverage = Math.round(observed + averageInterval);
        return Math.min(BUCKET_MS, estimatedCoverage) / 3_600_000.0;
    }

    private long trendKey(long ts, int days) {
        ZonedDateTime time = Instant.ofEpochMilli(ts).atZone(CONTROL_ZONE);
        ZonedDateTime truncated = days <= 1
                ? time.truncatedTo(ChronoUnit.HOURS)
                : time.toLocalDate().atStartOfDay(CONTROL_ZONE);
        return truncated.toInstant().toEpochMilli();
    }

    private double rate(Accumulator accumulator) {
        return accumulator.baseline <= 0 ? 0 : accumulator.saved / accumulator.baseline * 100;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static class Accumulator {
        double baseline;
        double estimated;
        double saved;
        double hours;

        void add(double baseline, double estimated, double saved, double hours) {
            this.baseline += baseline;
            this.estimated += estimated;
            this.saved += saved;
            this.hours += hours;
        }
    }

    private static class PeriodAccumulator extends Accumulator {
        final String name;
        final int brightnessPercent;

        PeriodAccumulator(String name, int brightnessPercent) {
            this.name = name;
            this.brightnessPercent = brightnessPercent;
        }
    }
}
