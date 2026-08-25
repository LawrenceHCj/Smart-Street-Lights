package com.smartlamp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TelemetryDTO {
    private Long id;
    private String deviceId;
    private Double lux;
    private Double temperature;
    private Double voltage;
    private Double current;
    private Double power;
    private Double energy;
    private String lampStatus;
    private Long timestamp;
    private String source;
}
