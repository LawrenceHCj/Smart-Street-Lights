package com.smartlamp.dto;

import lombok.Data;

@Data
public class AddDeviceRequest {
    private String code;       // 必填，唯一
    private String location;   // 可选
    private Double longitude;  // 经度，必填，范围 -180 ~ 180
    private Double latitude;   // 纬度，必填，范围 -90 ~ 90
}