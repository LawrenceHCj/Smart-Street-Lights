package com.smartlamp.dto;

import lombok.Data;

@Data
public class DeviceDTO {
    private Long id;
    private String code;
    private String location;
    private Double longitude;
    private Double latitude;
    private String status;
    private Double latestLux;
    private Long lastSeen;


    // 无参构造（由 Lombok @Data 自动生成，这里显式写出以便理解）
    public DeviceDTO() {}
}