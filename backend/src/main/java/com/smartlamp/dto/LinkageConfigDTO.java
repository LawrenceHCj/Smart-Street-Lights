package com.smartlamp.dto;

import lombok.Data;

import java.util.List;

@Data
public class LinkageConfigDTO {
    private boolean enabled;
    private int threshold;
    /**
     * 灯已点亮后，照度需要高于“开灯阈值 + 滞回值”才会关闭，避免临界照度反复开关。
     */
    private int hysteresis;

    /**
     * 分时调光与光照开关联动彼此独立。使用包装类型是为了兼容旧客户端：
     * PUT 请求未携带这些字段时，后端保留原有分时配置。
     */
    private Boolean brightnessScheduleEnabled;
    private List<BrightnessPeriodDTO> brightnessPeriods;

    /** 以下字段仅由 GET 返回，用于界面展示当前生效时段。 */
    private Integer currentBrightnessPercent;
    private String currentBrightnessPeriod;
    private String currentTime;
}
