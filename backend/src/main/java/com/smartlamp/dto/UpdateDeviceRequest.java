package com.smartlamp.dto;

import lombok.Data;

@Data
public class UpdateDeviceRequest {
    private String name;
    private String location;
    private String binding;
    private Boolean bound;
}
