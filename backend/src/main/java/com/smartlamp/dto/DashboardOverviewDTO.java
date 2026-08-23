package com.smartlamp.dto;

import lombok.Data;

@Data
public class DashboardOverviewDTO {
    private long totalDevices;
    private long onlineCount;
    private long offlineCount;
    private Double avgLux;

    public DashboardOverviewDTO(long totalDevices, long onlineCount, long offlineCount, Double avgLux) {
        this.totalDevices = totalDevices;
        this.onlineCount = onlineCount;
        this.offlineCount = offlineCount;
        this.avgLux = avgLux;
    }
}