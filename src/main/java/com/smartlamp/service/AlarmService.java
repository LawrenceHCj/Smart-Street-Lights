package com.smartlamp.service;

import com.smartlamp.entity.Alarm;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.enums.AlarmLevel;
import com.smartlamp.entity.enums.AlarmStatus;
import com.smartlamp.repository.AlarmRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlarmService {

    @Autowired
    private AlarmRepository alarmRepository;

    // 生成离线告警
    public void createOfflineAlarm(Device device) {
        Alarm alarm = new Alarm();
        alarm.setDeviceId(device.getCode());
        alarm.setType("离线");
        alarm.setLevel(AlarmLevel.WARNING);          // 改为枚举
        alarm.setMessage("设备心跳中断超过阈值时间");
        alarm.setTs(System.currentTimeMillis());
        alarm.setStatus(AlarmStatus.OPEN);           // 改为枚举
        alarm.setCreatedAt(LocalDateTime.now());
        alarmRepository.save(alarm);
    }

    // 获取所有告警
    public List<Alarm> getAllAlarms() {
        return alarmRepository.findAllByOrderByTsDesc();
    }

    // 确认告警
    public boolean ackAlarm(Long id) {
        Alarm alarm = alarmRepository.findById(id).orElse(null);
        if (alarm == null) {
            return false;
        }
        alarm.setStatus(AlarmStatus.ACKED);          // 改为枚举
        alarmRepository.save(alarm);
        return true;
    }
}