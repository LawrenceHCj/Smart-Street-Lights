package com.smartlamp.dto;

import lombok.Data;

@Data
public class UpdateDeviceRequest {
    private String location;   // 可选
    private Double longitude;  // 可选，范围 -180 ~ 180
    private Double latitude;   // 可选，范围 -90 ~ 90
}