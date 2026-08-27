package com.smartlamp.dto;

import lombok.Data;

@Data
public class SystemConfigDTO {
    private boolean autoControl;
    private int luxThreshold;
    private int hysteresis;
    private int heartbeatTimeoutMs;
}
