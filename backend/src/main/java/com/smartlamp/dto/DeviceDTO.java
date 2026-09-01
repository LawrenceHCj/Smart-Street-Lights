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
    private String lampStatus;
    private Double latestLux;
    private Long lastSeen;

    // 构造方法，从 Device 实体转换
    public DeviceDTO(Long id, String code, String location, Double longitude, Double latitude,
                     String status, String lampStatus, Double latestLux, Long lastSeen) {
        this.id = id;
        this.code = code;
        this.location = location;
        this.longitude = longitude;
        this.latitude = latitude;
        this.status = status;
        this.lampStatus = lampStatus;
        this.latestLux = latestLux;
        this.lastSeen = lastSeen;
    }
}
