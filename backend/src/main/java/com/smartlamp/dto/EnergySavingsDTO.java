package com.smartlamp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnergySavingsDTO {
    private long generatedAt;
    private long startTs;
    private long endTs;
    private int days;
    private int coveredDeviceCount;
    private long sampleBucketCount;
    private double coverageHours;
    private double baselineEnergyKwh;
    private double estimatedEnergyKwh;
    private double savedEnergyKwh;
    private double savingRatePercent;
    private double averageBrightnessPercent;
    private double estimatedCostSavingYuan;
    private double estimatedCarbonReductionKg;
    private double electricityPriceYuanPerKwh;
    private double carbonFactorKgPerKwh;
    private String calculationMethod;
    private List<TrendPoint> trend;
    private List<DeviceSaving> devices;
    private List<PeriodSaving> periods;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private long ts;
        private double baselineEnergyKwh;
        private double estimatedEnergyKwh;
        private double savedEnergyKwh;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceSaving {
        private String deviceId;
        private double baselineEnergyKwh;
        private double estimatedEnergyKwh;
        private double savedEnergyKwh;
        private double savingRatePercent;
        private double coverageHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodSaving {
        private String name;
        private int brightnessPercent;
        private double baselineEnergyKwh;
        private double estimatedEnergyKwh;
        private double savedEnergyKwh;
        private double coverageHours;
    }
}
