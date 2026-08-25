package com.smartlamp.dto;

import lombok.Data;

@Data
public class DeviceControlRequest {
    private String action;   // 取值 ON 或 OFF
}