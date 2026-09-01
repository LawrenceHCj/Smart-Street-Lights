package com.smartlamp.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchDeviceModeRequest {
    private List<String> deviceIds;
    private String mode;
}
