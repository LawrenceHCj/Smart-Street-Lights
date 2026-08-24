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

    // 构造方法，从 Device 实体转换
    public DeviceDTO(Long id, String code, String location, Double longitude, Double latitude,
                     String status, Double latestLux, Long lastSeen) {
        this.id = id;
        this.code = code;
        this.location = location;
        this.longitude = longitude;
        this.latitude = latitude;
        this.status = status;
        this.latestLux = latestLux;
        this.lastSeen = lastSeen;
    }

    /** Kept for existing callers that do not yet provide map coordinates. */
    public DeviceDTO(Long id, String code, String location, String status, Double latestLux, Long lastSeen) {
        this(id, code, location, null, null, status, latestLux, lastSeen);
    }
}
