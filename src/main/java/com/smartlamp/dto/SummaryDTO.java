package com.smartlamp.dto;

import lombok.Data;

@Data
public class SummaryDTO {
    private long totalDevices;
    private long onlineCount;
    private long offlineCount;
    private double avgLux;
    private LightDataDTO lastTelemetry;  // 最近一条光照数据，可能为 null

    public SummaryDTO(long totalDevices, long onlineCount, long offlineCount, double avgLux, LightDataDTO lastTelemetry) {
        this.totalDevices = totalDevices;
        this.onlineCount = onlineCount;
        this.offlineCount = offlineCount;
        this.avgLux = avgLux;
        this.lastTelemetry = lastTelemetry;
    }
}