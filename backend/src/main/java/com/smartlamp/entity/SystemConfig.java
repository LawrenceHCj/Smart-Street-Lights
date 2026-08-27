package com.smartlamp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
