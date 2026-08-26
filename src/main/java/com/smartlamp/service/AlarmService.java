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

    // 生成离线告警（带去重）
    public void createOfflineAlarm(Device device) {
        String deviceId = device.getCode();
        String type = "离线";
        String message = "设备心跳中断超过阈值时间";

        List<Alarm> existingAlarms = alarmRepository.findRecentByDeviceAndType(
                deviceId, type, List.of(AlarmStatus.OPEN, AlarmStatus.ACKED));

        if (!existingAlarms.isEmpty()) {
            Alarm alarm = existingAlarms.get(0);
            // 修复：处理 occurrenceCount 可能为 null 的情况
            Integer currentCount = alarm.getOccurrenceCount();
            alarm.setOccurrenceCount((currentCount == null ? 0 : currentCount) + 1);
            alarm.setLastOccurredAt(LocalDateTime.now());
            alarm.setTs(System.currentTimeMillis());
            alarm.setStatus(AlarmStatus.OPEN);
            alarm.setLevel(AlarmLevel.WARNING);
            alarmRepository.save(alarm);
            System.out.println("设备 " + deviceId + " 离线告警已更新，发生次数 " + alarm.getOccurrenceCount());
        } else {
            Alarm alarm = new Alarm();
            alarm.setDeviceId(deviceId);
            alarm.setType(type);
            alarm.setLevel(AlarmLevel.WARNING);
            alarm.setMessage(message);
            alarm.setTs(System.currentTimeMillis());
            alarm.setStatus(AlarmStatus.OPEN);
            alarm.setFirstOccurredAt(LocalDateTime.now());
            alarm.setLastOccurredAt(LocalDateTime.now());
            alarm.setOccurrenceCount(1);
            alarm.setCreatedAt(LocalDateTime.now());
            alarmRepository.save(alarm);
            System.out.println("设备 " + deviceId + " 产生新的离线告警");
        }
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
        alarm.setStatus(AlarmStatus.ACKED);
        alarmRepository.save(alarm);
        return true;
    }

    // 设备恢复在线时，自动恢复该设备所有未恢复的离线告警
    public void recoverOfflineAlarms(String deviceId) {
        List<Alarm> alarms = alarmRepository.findUnrecoveredOfflineAlarms(deviceId);
        for (Alarm alarm : alarms) {
            alarm.setStatus(AlarmStatus.RECOVERED);
            alarm.setLastOccurredAt(LocalDateTime.now());
            alarmRepository.save(alarm);
            System.out.println("设备 " + deviceId + " 离线告警已恢复");
        }
    }
}