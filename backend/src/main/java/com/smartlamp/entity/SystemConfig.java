package com.smartlamp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Data;

@Data
@Entity
@Table(name = "system_config")
public class SystemConfig {
    @Id
    private Long id = 1L;
    private boolean autoControl = true;
    private int luxThreshold = 30;
    private int hysteresis = 10;
    private int heartbeatTimeoutMs = 30000;
    private String simulatorScenario = "normal";
    /** 分时调光不改变 Lux 开关策略；关闭时统一恢复 100% 亮度。 */
    private boolean brightnessScheduleEnabled = true;
    /** 格式：HH:mm|名称|百分比;...，采用文本持久化以便平滑升级既有单行配置表。 */
    @Column(length = 2000)
    private String brightnessSchedule = "00:00|深夜节能|50;05:00|清晨照明|80;07:00|日间待机|100;18:00|傍晚照明|100;23:00|夜间节能|70";
}
