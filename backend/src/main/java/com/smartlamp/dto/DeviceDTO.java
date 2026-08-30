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
    // 【5号代做·需与3号对账】补充灯开关状态与绑定状态：
    // 智能体回答"哪些灯是打开的"等设备状态问题时需要这两个字段（Device 实体已有，DTO 原本未透出）。
    // 行为变化点：/api/devices 与智能体设备列表/状态查询多返回 lampStatus、bound 两个字段（缺省为 null）。
    private String lampStatus;
    private Boolean bound;

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
