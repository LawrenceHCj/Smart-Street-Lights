package com.smartlamp.dto;

import lombok.Data;

@Data
public class LinkageConfigDTO {
    private boolean enabled;
    private int threshold;
    /**
     * 灯已点亮后，照度需要高于“开灯阈值 + 滞回值”才会关闭，避免临界照度反复开关。
     */
    private int hysteresis;
}
