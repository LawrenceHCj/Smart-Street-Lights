package com.smartlamp.task;

import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.service.AlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeviceOfflineTask {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AlarmService alarmService;

    // 每 30 秒执行一次
    @Scheduled(fixedRate = 30000)
    public void checkOfflineDevices() {
        List<Device> devices = deviceRepository.findAll();
        long now = System.currentTimeMillis();
        long threshold = 90 * 1000; // 90 秒

        for (Device device : devices) {
            // 只处理当前状态为 ONLINE 且 lastSeen 不为空的设备
            if ("ONLINE".equals(device.getStatus()) && device.getLastSeen() != null) {
                if (now - device.getLastSeen() > threshold) {
                    device.setStatus("OFFLINE");
                    deviceRepository.save(device);
                    System.out.println("设备 " + device.getCode() + " 已自动标记为 OFFLINE");

                    // 生成离线告警
                    alarmService.createOfflineAlarm(device);
                }
            }
        }
    }
}