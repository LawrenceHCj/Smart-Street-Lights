package com.smartlamp.service;

import com.smartlamp.entity.Alarm;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.AlarmRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlarmService {
    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final AlarmRepository alarmRepository;

    public AlarmService(AlarmRepository alarmRepository) {
        this.alarmRepository = alarmRepository;
    }

    @Transactional
    public void createOfflineAlarm(Device device) {
        String deviceId = device.getCode();
        LocalDateTime now = LocalDateTime.now();
        List<Alarm> existing = alarmRepository.findRecentByDeviceAndType(
                deviceId, "离线", List.of("OPEN", "ACKED"));

        if (!existing.isEmpty()) {
            Alarm alarm = existing.get(0);
            alarm.setOccurrenceCount((alarm.getOccurrenceCount() == null ? 0 : alarm.getOccurrenceCount()) + 1);
            if (alarm.getFirstOccurredAt() == null) alarm.setFirstOccurredAt(alarm.getCreatedAt());
            alarm.setLastOccurredAt(now);
            alarm.setTs(System.currentTimeMillis());
            alarm.setStatus("OPEN");
            alarm.setLevel("warning");
            alarmRepository.save(alarm);
            log.info("设备 {} 离线告警已合并，累计 {} 次", deviceId, alarm.getOccurrenceCount());
            return;
        }

        Alarm alarm = new Alarm();
        alarm.setDeviceId(deviceId);
        alarm.setType("离线");
        alarm.setLevel("warning");
        alarm.setMessage("设备心跳中断超过阈值时间");
        alarm.setTs(System.currentTimeMillis());
        alarm.setStatus("OPEN");
        alarm.setFirstOccurredAt(now);
        alarm.setLastOccurredAt(now);
        alarm.setOccurrenceCount(1);
        alarm.setCreatedAt(now);
        alarmRepository.save(alarm);
    }

    public List<Alarm> getAllAlarms() {
        return alarmRepository.findAllByOrderByTsDesc();
    }

    @Transactional
    public boolean ackAlarm(Long id) {
        Alarm alarm = alarmRepository.findById(id).orElse(null);
        if (alarm == null) return false;
        alarm.setStatus("ACKED");
        alarmRepository.save(alarm);
        return true;
    }

    @Transactional
    public void recoverOfflineAlarms(String deviceId) {
        LocalDateTime now = LocalDateTime.now();
        List<Alarm> alarms = alarmRepository.findUnrecoveredOfflineAlarms(deviceId);
        for (Alarm alarm : alarms) {
            alarm.setStatus("RECOVERED");
            alarm.setLastOccurredAt(now);
            alarmRepository.save(alarm);
        }
        if (!alarms.isEmpty()) log.info("设备 {} 的 {} 条离线告警已自动恢复", deviceId, alarms.size());
    }
}
