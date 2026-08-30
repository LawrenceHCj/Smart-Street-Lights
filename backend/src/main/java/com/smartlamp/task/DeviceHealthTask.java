package com.smartlamp.task;

import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.service.DeviceHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class DeviceHealthTask {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceHealthService healthService;

    @Autowired
    private com.smartlamp.repository.DeviceHealthReportRepository healthReportRepository;

    /** 健康报告保留天数，默认 7 天，过期自动清理。 */
    @Value("${health.report.retention-days:7}")
    private int retentionDays;

    // 改为每小时巡检一次（原 60 秒一次会造成每台设备每天约 1440 条记录，表无限增长）。
    // 演示时可临时在 application.yml 中覆盖为更短间隔；生产环境可改为每天凌晨执行：
    // @Scheduled(cron = "0 0 2 * * ?")
    @Scheduled(fixedDelayString = "${health.report.inspect-interval-ms:3600000}")
    public void autoInspectDevices() {
        log.info("开始执行自动化设备健康巡检...");

        // 1. 从数据库查出所有的路灯设备
        List<Device> devices = deviceRepository.findAll();
        int evaluated = 0;
        int skipped = 0;

        // 2. 逐个丢进写好的白盒规则引擎里算分
        for (Device device : devices) {
            // evaluateDeviceHealth 现在会在"无遥测/遥测陈旧"时返回 null，不再写入满分报告
            var report = healthService.evaluateDeviceHealth(device);
            if (report == null) {
                skipped++;
            } else {
                evaluated++;
            }
        }

        log.info("自动化巡检完成：共 {} 台设备，{} 台生成报告，{} 台因数据不足/陈旧跳过",
                devices.size(), evaluated, skipped);

        // 3. 清理过期报告，避免表无限增长
        try {
            LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
            int deleted = healthReportRepository.deleteOlderThan(before);
            if (deleted > 0) {
                log.info("已清理 {} 条超过 {} 天的健康报告", deleted, retentionDays);
            }
        } catch (Exception e) {
            // 保留策略失败不应中断巡检
            log.warn("清理过期健康报告失败: {}", e.getMessage());
        }
    }
}
