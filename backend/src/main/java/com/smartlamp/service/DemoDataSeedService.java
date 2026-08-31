package com.smartlamp.service;

import com.smartlamp.entity.Alarm;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.AlarmRepository;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.LightPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 演示数据种子生成器（预测性维护答辩用，仅手动触发）。
 *
 * 生成 4 台虚拟设备、近 7 天每 30 分钟一条的合成遥测（约 1350 条 LightPoint），
 * 使三种风险等级都能真实演出来：
 *   SL-DEMO-A 健康对照      —— 各项平稳                         → 预期 LOW
 *   SL-DEMO-B 温度爬升+电流漂移 —— 温度以 ~2.9℃/天 攀升，外推触告警线 → 预期 HIGH（一票升级）
 *   SL-DEMO-C 供电波动+电流漂移 —— 电压大幅波动、电流缓慢漂移        → 预期 MEDIUM
 *   SL-DEMO-D 离线频发      —— 近 30 天 4 次离线告警              → 离线特征贡献可见
 *
 * 设计要点：
 *   - 幂等：任一 SL-DEMO-* 设备已存在则整体跳过，避免重复插入
 *   - 固定随机种子 42：同样输入永远同样数据，答辩可复现
 *   - 白天/黑夜模拟：19:00~06:00 灯亮（温度/电流为负载值），白天灭灯（温度回落环境值）
 */
@Service
public class DemoDataSeedService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeedService.class);
    private static final long STEP_MS = 30L * 60 * 1000;
    private static final long WINDOW_7D_MS = 7L * 24 * 3600 * 1000;

    private final DeviceRepository deviceRepository;
    private final LightPointRepository lightPointRepository;
    private final AlarmRepository alarmRepository;

    public DemoDataSeedService(DeviceRepository deviceRepository,
                               LightPointRepository lightPointRepository,
                               AlarmRepository alarmRepository) {
        this.deviceRepository = deviceRepository;
        this.lightPointRepository = lightPointRepository;
        this.alarmRepository = alarmRepository;
    }

    @Transactional
    public String seed() {
        List<String> demoCodes = List.of("SL-DEMO-A", "SL-DEMO-B", "SL-DEMO-C", "SL-DEMO-D");
        for (String code : demoCodes) {
            if (deviceRepository.findByCode(code).isPresent()) {
                return "演示数据已存在（" + code + " 等），未重复生成。如需重新生成请先删除 SL-DEMO-* 设备。";
            }
        }

        Random random = new Random(42);
        long now = System.currentTimeMillis();
        long start = now - WINDOW_7D_MS;
        int points = (int) (WINDOW_7D_MS / STEP_MS);

        seedDevice("SL-DEMO-A", "健康对照", "实验楼南侧", random, now, start, points, Profile.HEALTHY);
        seedDevice("SL-DEMO-B", "温度爬升+电流漂移", "实验楼北侧", random, now, start, points, Profile.THERMAL_RUNAWAY);
        seedDevice("SL-DEMO-C", "供电波动+电流漂移", "图书馆东侧", random, now, start, points, Profile.POWER_QUALITY);
        seedDevice("SL-DEMO-D", "离线频发", "风雨操场", random, now, start, points, Profile.FLAT);
        seedOfflineAlarms("SL-DEMO-D");

        String summary = "已生成 4 台演示设备（近 7 天遥测，每 30 分钟一条）："
                + "SL-DEMO-A 健康对照(预期LOW)、SL-DEMO-B 温度爬升(预期HIGH)、"
                + "SL-DEMO-C 供电波动(预期MEDIUM)、SL-DEMO-D 离线频发(离线特征可见)。";
        log.info(summary);
        return summary;
    }

    // ==================== 设备画像 ====================

    private enum Profile { HEALTHY, THERMAL_RUNAWAY, POWER_QUALITY, FLAT }

    private void seedDevice(String code, String name, String location, Random random,
                            long now, long start, int points, Profile profile) {
        Device device = new Device();
        device.setCode(code);
        device.setName(name);
        device.setLocation(location);
        device.setLongitude(106.47 + random.nextDouble() * 0.01);
        device.setLatitude(29.57 + random.nextDouble() * 0.01);
        device.setBinding("unbound");
        device.setStatus("ONLINE");
        device.setLampStatus("OFF");
        device.setLastSeen(now);
        device.setLastTelemetryAt(now);
        device.setCreatedAt(LocalDateTime.now());

        List<LightPoint> batch = new ArrayList<>(points);
        double daysElapsed = 0;
        for (int i = 0; i < points; i++) {
            long ts = start + (long) i * STEP_MS;
            daysElapsed = (double) i / points * 7.0;
            LocalDateTime t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), ZoneId.systemDefault());
            int hour = t.getHour();
            boolean on = hour >= 19 || hour < 6;

            double lux;
            if (on) lux = 5 + random.nextDouble() * 20;
            else lux = 20000 + 30000 * Math.sin(Math.PI * (hour - 6) / 13.0) + random.nextDouble() * 2000;

            double temp, current, power, voltage;
            switch (profile) {
                case THERMAL_RUNAWAY -> {
                    // 温度 42 → 62 线性爬升（约 2.9℃/天），电流 1.5 → 1.9 缓慢漂移
                    temp = on ? 42 + (62 - 42) * daysElapsed / 7.0 + rnd(random, 1.0) : 26 + rnd(random, 2.0);
                    current = on ? 1.5 + 0.4 * daysElapsed / 7.0 + rnd(random, 0.03) : 0;
                    power = on ? 60 + 0.6 * daysElapsed / 7.0 + rnd(random, 1.5) : 0;
                    voltage = 220 + rnd(random, 1.5);
                }
                case POWER_QUALITY -> {
                    // 电压 ±70V 大幅波动（CV≈0.18），电流 1.3 → 1.9 明显漂移
                    voltage = 220 + rnd(random, 70);
                    temp = on ? 40 + rnd(random, 1.5) : 26 + rnd(random, 2.0);
                    current = on ? 1.3 + 0.6 * daysElapsed / 7.0 + rnd(random, 0.03) : 0;
                    power = on ? 58 + rnd(random, 2.0) : 0;
                }
                case FLAT -> {
                    temp = on ? 40 + rnd(random, 1.2) : 26 + rnd(random, 2.0);
                    current = on ? 1.5 + rnd(random, 0.03) : 0;
                    power = on ? 58 + rnd(random, 1.5) : 0;
                    voltage = 220 + rnd(random, 1.5);
                }
                default -> { // HEALTHY
                    temp = on ? 40 + rnd(random, 1.2) : 26 + rnd(random, 2.0);
                    current = on ? 1.5 + rnd(random, 0.05) : 0;
                    power = on ? 58 + rnd(random, 2.0) : 0;
                    voltage = 220 + rnd(random, 1.5);
                }
            }

            LightPoint point = new LightPoint();
            point.setDeviceCode(code);
            point.setTs(ts);
            point.setLux(round2(lux));
            point.setTemperature(round2(temp));
            point.setVoltage(round2(voltage));
            point.setCurrent(round2(current));
            point.setPower(round2(power));
            point.setEnergy(round2(3.0 * i / points));
            point.setLampStatus(on ? "ON" : "OFF");
            point.setServerReceivedAt(LocalDateTime.now());
            batch.add(point);
        }
        lightPointRepository.saveAll(batch);

        // Device 快照取最后一个采样点（当前为白天灭灯态）
        LightPoint last = batch.get(batch.size() - 1);
        device.setLatestLux(last.getLux());
        device.setLatestTemperature(last.getTemperature());
        device.setLatestVoltage(last.getVoltage());
        device.setLatestCurrent(last.getCurrent());
        device.setLatestPower(last.getPower());
        device.setLatestEnergy(last.getEnergy());
        device.setLampStatus(last.getLampStatus());
        deviceRepository.save(device);
    }

    /** 为 SL-DEMO-D 撒 4 条近 30 天的离线告警（已恢复状态，不污染活跃告警列表） */
    private void seedOfflineAlarms(String code) {
        LocalDateTime now = LocalDateTime.now();
        int[] daysAgo = {2, 9, 19, 27};
        for (int d : daysAgo) {
            LocalDateTime at = now.minusDays(d).withHour(3).withMinute((int) (Math.random() * 50));
            Alarm alarm = new Alarm();
            alarm.setDeviceId(code);
            alarm.setType("离线");
            alarm.setLevel("warning");
            alarm.setMessage("设备心跳中断超过阈值时间");
            alarm.setTs(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            alarm.setStatus("RECOVERED");
            alarm.setFirstOccurredAt(at);
            alarm.setLastOccurredAt(at.plusMinutes(20));
            alarm.setOccurrenceCount(1);
            alarm.setCreatedAt(at);
            alarmRepository.save(alarm);
        }
    }

    private static double rnd(Random random, double amplitude) {
        return (random.nextDouble() * 2 - 1) * amplitude;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
