package com.smartlamp.dto;

import lombok.Data;

@Data
public class AddDeviceRequest {
    private String code;       // 必填，唯一
    private String location;   // 可选
}