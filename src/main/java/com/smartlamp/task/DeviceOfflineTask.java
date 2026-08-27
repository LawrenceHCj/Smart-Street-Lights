package com.smartlamp.task;

import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.service.AlarmService;
import com.smartlamp.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeviceOfflineTask {
    private static final Logger log = LoggerFactory.getLogger(DeviceOfflineTask.class);

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private ConfigService configService;

    // 每 30 秒执行一次
    @Scheduled(fixedRate = 30000)
    public void checkOfflineDevices() {
        List<Device> devices = deviceRepository.findAll();
        long now = System.currentTimeMillis();
        long threshold = configService.getConfig().getHeartbeatTimeoutMs();

        for (Device device : devices) {
            // 只处理当前状态为 ONLINE 且 lastSeen 不为空的设备
            if ("ONLINE".equals(device.getStatus()) && device.getLastSeen() != null) {
                if (now - device.getLastSeen() > threshold) {
                    device.setStatus("OFFLINE");
                    deviceRepository.save(device);
                    log.info("设备 {} 已自动标记为 OFFLINE", device.getCode());

                    // 生成离线告警
                    alarmService.createOfflineAlarm(device);
                }
            }
        }
    }
}
