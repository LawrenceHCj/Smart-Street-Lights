package com.smartlamp.task;

import com.smartlamp.repository.DeviceHealthReportRepository;
import com.smartlamp.repository.LightPointRepository;
import com.smartlamp.repository.MqttDeadLetterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class DataRetentionTask {

    @Autowired
    private LightPointRepository lightPointRepository;

    @Autowired
    private MqttDeadLetterRepository mqttDeadLetterRepository;

    @Autowired
    private DeviceHealthReportRepository healthReportRepository;

    // 每天凌晨 3 点执行清理
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanExpiredData() {
        // 1. 清理光照历史（默认保留30天）
        long lightPointCutoff = System.currentTimeMillis() - (30L * 24 * 3600 * 1000);
        lightPointRepository.deleteByTsBefore(lightPointCutoff);

        // 2. 清理死信数据（保留7天）
        LocalDateTime deadLetterCutoff = LocalDateTime.now().minusDays(7);
        mqttDeadLetterRepository.deleteByReceivedAtBefore(deadLetterCutoff);

        // 3. 清理健康报告（保留30天）
        LocalDateTime healthCutoff = LocalDateTime.now().minusDays(30);
        healthReportRepository.deleteByCreatedAtBefore(healthCutoff);

        System.out.println("数据保留清理任务完成");
    }
}