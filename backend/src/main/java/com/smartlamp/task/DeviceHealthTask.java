package com.smartlamp.task;

import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.service.DeviceHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DeviceHealthTask {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceHealthService healthService;

    // @Scheduled 里的 fixedRate = 60000 代表每 60 秒（1分钟）执行一次。
    // 为了答辩时能立刻看到效果，现在先设成 1 分钟。
    // 等项目完全定稿要上线时，可以改成每天凌晨 2 点执行：@Scheduled(cron = "0 0 2 * * ?")
    @Scheduled(fixedRate = 60000)
    public void autoInspectDevices() {
        log.info("开始执行自动化设备健康巡检...");

        // 1. 从数据库查出所有的路灯设备
        List<Device> devices = deviceRepository.findAll();
        int count = 0;

        // 2. 挨个丢进写好的白盒规则引擎里算分
        for (Device device : devices) {
            healthService.evaluateDeviceHealth(device);
            count++;
        }

        log.info("自动化巡检完成，共为 {} 台设备生成了体检报告！", count);
    }
}