package com.smartlamp.dto;

import lombok.Data;

@Data
public class LinkageConfigDTO {
    private boolean enabled;
    private int threshold;
    private int hysteresis = 5;          // 滞回值，默认 5
    private long heartbeatTimeoutMs = 90000; // 心跳超时毫秒，默认 90 秒
}