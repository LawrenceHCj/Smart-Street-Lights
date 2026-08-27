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

    // 改为每小时执行一次（3600000ms）
    @Scheduled(fixedDelay = 3600000)
    public void autoInspectDevices() {
        log.info("开始执行自动化设备健康巡检...");
        List<Device> devices = deviceRepository.findAll();
        int count = 0;
        for (Device device : devices) {
            healthService.evaluateDeviceHealth(device);
            count++;
        }
        log.info("自动化巡检完成，共为 {} 台设备生成了体检报告！", count);
    }
}