package com.smartlamp.service;

import com.smartlamp.dto.BrightnessPeriodDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.dto.SystemConfigDTO;
import com.smartlamp.entity.SystemConfig;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.SystemConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ConfigService {

    private static final ZoneId CONTROL_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_BRIGHTNESS_SCHEDULE =
            "00:00|深夜节能|50;05:00|清晨照明|80;07:00|日间待机|100;18:00|傍晚照明|100;23:00|夜间节能|70";

    /** 防止每 30 秒重复下发；只在跨时段、启停策略或保存配置时重新发布。 */
    private final AtomicReference<String> lastBrightnessSignature = new AtomicReference<>();

    @Autowired
    private SystemConfigRepository repository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    private SystemConfig config() {
        SystemConfig config = repository.findById(1L).orElseGet(() -> repository.save(new SystemConfig()));
        // Hibernate 给既有数据库新增列时，旧的单行配置没有实体字段默认值；在首次读取时完成安全迁移。
        boolean needsSave = false;
        if (config.getBrightnessSchedule() == null || config.getBrightnessSchedule().isBlank()) {
            config.setBrightnessSchedule(DEFAULT_BRIGHTNESS_SCHEDULE);
            needsSave = true;
        }
        // 分时亮度是常驻系统能力，不再提供启停入口。
        if (!config.isBrightnessScheduleEnabled()) {
            config.setBrightnessScheduleEnabled(true);
            needsSave = true;
        }
        if (needsSave) repository.save(config);
        return config;
    }

    public LinkageConfigDTO getLinkageConfig() {
        return toLinkageConfig(config(), LocalTime.now(CONTROL_ZONE));
    }

    private LinkageConfigDTO toLinkageConfig(SystemConfig config, LocalTime now) {
        LinkageConfigDTO dto = new LinkageConfigDTO();
        dto.setEnabled(config.isAutoControl());
        dto.setThreshold(config.getLuxThreshold());
        dto.setHysteresis(config.getHysteresis());
        dto.setBrightnessScheduleEnabled(true);
        List<BrightnessPeriodDTO> periods = parseBrightnessSchedule(config.getBrightnessSchedule());
        dto.setBrightnessPeriods(periods);
        BrightnessSelection selection = selectBrightness(periods, now, true);
        dto.setCurrentBrightnessPercent(selection.brightnessPercent());
        dto.setCurrentBrightnessPeriod(selection.periodName());
        dto.setCurrentTime(now.format(TIME_FORMAT));
        return dto;
    }

    /**
     * 为不具备本地联动逻辑的托管模拟设备计算灯态。
     * 真实设备仍在设备侧执行联动；滞回区间内保持当前状态，避免覆盖手动控制结果。
     */
    public String resolveSimulatorLampStatus(double lux, String currentStatus, String fallbackStatus) {
        SystemConfig config = config();
        if (!config.isAutoControl()) return fallbackStatus;
        if (lux < config.getLuxThreshold()) return "ON";
        if (lux > config.getLuxThreshold() + config.getHysteresis()) return "OFF";
        return currentStatus != null ? currentStatus : fallbackStatus;
    }

    public void saveLinkageConfig(LinkageConfigDTO newConfig) {
        SystemConfig config = config();
        config.setAutoControl(newConfig.isEnabled());
        config.setLuxThreshold(newConfig.getThreshold());
        config.setHysteresis(newConfig.getHysteresis());
        // 兼容旧客户端和 Agent：未携带分时字段时只修改原有联动配置。
        config.setBrightnessScheduleEnabled(true);
        if (newConfig.getBrightnessPeriods() != null) {
            config.setBrightnessSchedule(serializeBrightnessSchedule(newConfig.getBrightnessPeriods()));
        }
        repository.save(config);
        setAllDeviceModes(newConfig.isEnabled() ? "AUTO" : "MANUAL");
        LinkageConfigDTO saved = toLinkageConfig(config, LocalTime.now(CONTROL_ZONE));
        publishLinkageConfig(saved);
        publishCurrentBrightness(LocalTime.now(CONTROL_ZONE), true);
    }

    private void publishLinkageConfig(LinkageConfigDTO config) {
        int offLux = config.getThreshold() + config.getHysteresis();
        String scheduleJson = brightnessPeriodsJson(config.getBrightnessPeriods());
        for (Device device : deviceRepository.findAll()) {
            if (!Boolean.TRUE.equals(device.getBound())) continue;
            String deviceId = device.getCode();
            String payload = "{\"deviceId\":\"" + deviceId
                    + "\",\"auto\":" + config.isEnabled()
                    + ",\"onLux\":" + config.getThreshold()
                    + ",\"offLux\":" + offLux
                    + ",\"brightnessScheduleEnabled\":true"
                    + ",\"brightnessPeriods\":" + scheduleJson + "}";
            mqttPublisherService.publish("device/" + deviceId + "/cmd", payload);
        }
    }

    /**
     * 每 30 秒检查一次当前时间段。只有目标百分比或时段发生变化时才下发，
     * 且 SET_BRIGHTNESS 不携带 ON/OFF 字段，不会改变原有 Lux 开关策略。
     */
    @Scheduled(fixedDelay = 30_000)
    public void applyTimeBasedBrightness() {
        publishCurrentBrightness(LocalTime.now(CONTROL_ZONE), false);
    }

    void publishCurrentBrightness(LocalTime now, boolean force) {
        SystemConfig config = config();
        List<BrightnessPeriodDTO> periods = parseBrightnessSchedule(config.getBrightnessSchedule());
        BrightnessSelection selection = selectBrightness(periods, now, true);
        String signature = selection.periodName() + ":" + selection.brightnessPercent();
        if (!force && signature.equals(lastBrightnessSignature.get())) return;

        boolean hadEligibleDevice = false;
        boolean allAccepted = true;
        for (Device device : deviceRepository.findAll()) {
            if (!Boolean.TRUE.equals(device.getBound())) continue;
            hadEligibleDevice = true;
            String deviceId = device.getCode();
            String payload = "{\"deviceId\":\"" + deviceId
                    + "\",\"action\":\"SET_BRIGHTNESS\",\"brightness\":"
                    + selection.brightnessPercent()
                    + ",\"source\":\"TIME_SCHEDULE\",\"period\":\""
                    + escapeJson(selection.periodName()) + "\"}";
            try {
                boolean accepted = mqttPublisherService.publish("device/" + deviceId + "/cmd", payload);
                if (!accepted) {
                    allAccepted = false;
                    log.warn("分时亮度指令未被发送通道接受: deviceId={}", deviceId);
                }
            } catch (RuntimeException error) {
                // 单台设备下发失败不阻断其他设备；下一次跨时段或保存配置时会重试。
                allAccepted = false;
                log.warn("分时亮度指令下发失败: deviceId={}, reason={}", deviceId, error.getMessage());
            }
        }
        // 无设备或部分失败时不记成功签名，定时任务将在 30 秒后重试。
        if (hadEligibleDevice && allAccepted) lastBrightnessSignature.set(signature);
    }

    int resolveBrightnessPercent(LocalTime now) {
        SystemConfig config = config();
        return selectBrightness(parseBrightnessSchedule(config.getBrightnessSchedule()), now, true).brightnessPercent();
    }

    /** 供节能统计复用同一套跨午夜时段判定，避免分析口径与控制口径漂移。 */
    public BrightnessPeriodDTO resolveBrightnessPeriod(List<BrightnessPeriodDTO> periods, LocalTime now) {
        BrightnessPeriodDTO selected = selectedPeriod(periods, now);
        return new BrightnessPeriodDTO(selected.getName(), selected.getStartTime(), selected.getBrightnessPercent());
    }

    private BrightnessSelection selectBrightness(List<BrightnessPeriodDTO> periods,
                                                  LocalTime now,
                                                  boolean enabled) {
        if (!enabled) return new BrightnessSelection("分时调光已关闭", 100);
        BrightnessPeriodDTO selected = selectedPeriod(periods, now);
        return new BrightnessSelection(selected.getName(), selected.getBrightnessPercent());
    }

    private BrightnessPeriodDTO selectedPeriod(List<BrightnessPeriodDTO> periods, LocalTime now) {
        List<BrightnessPeriodDTO> sorted = new ArrayList<>(periods);
        sorted.sort(Comparator.comparing(period -> LocalTime.parse(period.getStartTime(), TIME_FORMAT)));
        BrightnessPeriodDTO selected = sorted.get(sorted.size() - 1); // 午夜前沿用前一天最后一个时段
        for (BrightnessPeriodDTO period : sorted) {
            LocalTime start = LocalTime.parse(period.getStartTime(), TIME_FORMAT);
            if (start.isAfter(now)) break;
            selected = period;
        }
        return selected;
    }

    private List<BrightnessPeriodDTO> parseBrightnessSchedule(String stored) {
        String value = stored == null || stored.isBlank() ? DEFAULT_BRIGHTNESS_SCHEDULE : stored;
        try {
            List<BrightnessPeriodDTO> periods = new ArrayList<>();
            for (String item : value.split(";")) {
                String[] fields = item.split("\\|", -1);
                if (fields.length != 3) throw new IllegalArgumentException("invalid schedule item");
                LocalTime.parse(fields[0], TIME_FORMAT);
                int percent = Integer.parseInt(fields[2]);
                if (percent < 1 || percent > 100) throw new IllegalArgumentException("invalid brightness");
                periods.add(new BrightnessPeriodDTO(fields[1], fields[0], percent));
            }
            if (periods.isEmpty()) throw new IllegalArgumentException("empty schedule");
            return periods;
        } catch (RuntimeException error) {
            log.warn("分时亮度配置损坏，已回退默认策略: {}", error.getMessage());
            return parseBrightnessSchedule(DEFAULT_BRIGHTNESS_SCHEDULE);
        }
    }

    private String serializeBrightnessSchedule(List<BrightnessPeriodDTO> periods) {
        return periods.stream()
                .sorted(Comparator.comparing(period -> LocalTime.parse(period.getStartTime(), TIME_FORMAT)))
                .map(period -> period.getStartTime() + "|" + period.getName() + "|" + period.getBrightnessPercent())
                .reduce((left, right) -> left + ";" + right)
                .orElse(DEFAULT_BRIGHTNESS_SCHEDULE);
    }

    private String brightnessPeriodsJson(List<BrightnessPeriodDTO> periods) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < periods.size(); i++) {
            BrightnessPeriodDTO period = periods.get(i);
            if (i > 0) json.append(',');
            json.append("{\"startTime\":\"").append(period.getStartTime())
                    .append("\",\"brightness\":").append(period.getBrightnessPercent()).append('}');
        }
        return json.append(']').toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record BrightnessSelection(String periodName, int brightnessPercent) {}

    public SystemConfigDTO getConfig() {
        SystemConfig config = config();
        SystemConfigDTO dto = new SystemConfigDTO();
        dto.setAutoControl(config.isAutoControl());
        dto.setLuxThreshold(config.getLuxThreshold());
        dto.setHysteresis(config.getHysteresis());
        dto.setHeartbeatTimeoutMs(config.getHeartbeatTimeoutMs());
        return dto;
    }

    public void saveConfig(SystemConfigDTO dto) {
        SystemConfig config = config();
        config.setAutoControl(dto.isAutoControl());
        config.setLuxThreshold(dto.getLuxThreshold());
        config.setHysteresis(dto.getHysteresis());
        config.setHeartbeatTimeoutMs(dto.getHeartbeatTimeoutMs());
        repository.save(config);
        setAllDeviceModes(dto.isAutoControl() ? "AUTO" : "MANUAL");

        LinkageConfigDTO linkage = toLinkageConfig(config, LocalTime.now(CONTROL_ZONE));
        publishLinkageConfig(linkage);
        publishCurrentBrightness(LocalTime.now(CONTROL_ZONE), true);
    }

    private void setAllDeviceModes(String mode) {
        for (Device device : deviceRepository.findAll()) {
            device.setControlMode(mode);
            deviceRepository.save(device);
        }
    }
}
