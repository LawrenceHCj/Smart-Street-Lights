package com.smartlamp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrightnessPeriodDTO {
    /** 展示名称，不参与开关灯判断。 */
    private String name;
    /** 本时段开始时间，格式 HH:mm；结束时间由下一时段的开始时间决定。 */
    private String startTime;
    /** 调光百分比只能为 1-100；0 不合法，避免亮度策略变相承担关灯职责。 */
    private int brightnessPercent;
}
