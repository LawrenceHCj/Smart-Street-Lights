package com.smartlamp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ControlResultDTO {
    private String commandId;
    private String deviceId;
    private String action;
    private String mode;
    private String status;
    private Long issuedAt;
    private String message;
}
