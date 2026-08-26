package com.smartlamp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "data-retention")
public class RetentionProperties {
    private int lightPointDays = 30;
    private int deadLetterDays = 7;

    public int getLightPointDays() { return lightPointDays; }
    public void setLightPointDays(int lightPointDays) { this.lightPointDays = lightPointDays; }

    public int getDeadLetterDays() { return deadLetterDays; }
    public void setDeadLetterDays(int deadLetterDays) { this.deadLetterDays = deadLetterDays; }
}