package com.smartlamp.dto;

import lombok.Data;

@Data
public class LightDataDTO {
    private String deviceId;
    private Double lux;
    private Long ts;

    public LightDataDTO(String deviceId, Double lux, Long ts) {
        this.deviceId = deviceId;
        this.lux = lux;
        this.ts = ts;
    }
}