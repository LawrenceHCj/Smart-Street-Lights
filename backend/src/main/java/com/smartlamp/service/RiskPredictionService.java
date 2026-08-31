package com.smartlamp.service;

import com.smartlamp.agent.actions.ActionStatus;
import com.smartlamp.agent.actions.AgentActionAudit;
import com.smartlamp.agent.actions.AgentActionAuditRepository;
import com.smartlamp.entity.Alarm;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceHealthReport;
import com.smartlamp.entity.DeviceRiskPrediction;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.AlarmRepository;
import com.smartlamp.repository.DeviceHealthReportRepository;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.DeviceRiskPredictionRepository;
import com.smartlamp.repository.LightPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预测性维护：设备未来 7 天故障风险预测。
 *
 * 模型 = 滑动窗口特征提取 + 加权风险聚合（第一版，规则可解释，不上深度学习）：
 *
 *   7 特征（各映射为 0~1 风险分，样本不足的特征跳过并标注 insufficient）：
 *     1. voltage      电压波动率        7 天 voltage 变异系数 CV=σ/μ（仅统计 >0 的样本）
 *     2. current      电流偏移率        仅灯亮样本，窗口后半段均值 vs 前半段均值漂移率（正漂移视为老化）
 *     3. temperature  温度增长趋势      最小二乘斜率（℃/天），外推 7 天逼近 65℃ 告警线
 *     4. contradiction 功率/状态矛盾    power>0.5W 但 lampStatus=OFF 的样本占比（继电器/回传异常）
 *     5. offline      离线频率          近 30 天离线告警累计次数（离线告警按 occurrenceCount 合并存储）
 *     6. command      控制命令失败率    近 7 天 (FAILED+COMMAND_ACCEPTED)/(SUCCESS+FAILED+COMMAND_ACCEPTED)，
 *                                       COMMAND_ACCEPTED=设备始终未回执，同样视为设备侧异常
 *     7. fleet        同群横向偏差      该设备 7 天平均温度/电流与全体设备中位数的相对偏差
 *                                       （项目当前无区域字段，按拍板退化为全体设备互比）
 *
 *   聚合：综合风险 = Σ(wᵢ·rᵢ) / Σwᵢ（仅统计样本充足的特征）
 *   一票升级：温度外推 7 天内触及告警线 → 直接 HIGH（防止被低权重稀释）
 *   分级：≥0.60 HIGH，≥0.35 MEDIUM，否则 LOW
 *   建议：主导特征 → 确定性规则模板（同样输入永远同样输出，可追溯）
 */
@Service
public class RiskPredictionService {

    private static final Logger log = LoggerFactory.getLogger(RiskPredictionService.class);

    private static final long WINDOW_7D_MS = 7L * 24 * 3600 * 1000;
    private static final int MIN_SERIES_SAMPLES = 20;
    private static final int MIN_COMMAND_OUTCOMES = 3;
    private static final int MIN_FLEET_SIZE = 3;

    // ==================== 特征权重（application.yml 可覆盖，答辩可现场调参） ====================
    @Value("${risk.weight.temperature:0.25}") private double weightTemperature;
    @Value("${risk.weight.current:0.20}") private double weightCurrent;
    @Value("${risk.weight.voltage:0.15}") private double weightVoltage;
    @Value("${risk.weight.fleet:0.15}") private double weightFleet;
    @Value("${risk.weight.offline:0.10}") private double weightOffline;
    @Value("${risk.weight.command:0.10}") private double weightCommand;
    @Value("${risk.weight.contradiction:0.05}") private double weightContradiction;

    // ==================== 风险映射阈值 ====================
    @Value("${risk.threshold.voltage-cv:0.15}") private double voltageCvMax;
    @Value("${risk.threshold.current-drift:0.25}") private double currentDriftMax;
    @Value("${risk.threshold.temp-warn:55.0}") private double tempWarn;
    @Value("${risk.threshold.temp-alarm:65.0}") private double tempAlarm;
    @Value("${risk.threshold.contradiction-ratio:0.10}") private double contradictionRatioMax;
    @Value("${risk.threshold.offline-count:3.0}") private double offlineCountMax;
    @Value("${risk.threshold.command-failure:0.40}") private double commandFailureMax;
    @Value("${risk.threshold.fleet-deviation:0.30}") private double fleetDeviationMax;

    // ==================== 分级与保留 ====================
    @Value("${risk.level.high:0.60}") private double levelHigh;
    @Value("${risk.level.medium:0.35}") private double levelMedium;
    @Value("${risk.prediction.retention-days:30}") private int retentionDays;

    private final DeviceRepository deviceRepository;
    private final LightPointRepository lightPointRepository;
    private final AlarmRepository alarmRepository;
    private final AgentActionAuditRepository auditRepository;
    private final DeviceHealthReportRepository healthReportRepository;
    private final DeviceRiskPredictionRepository predictionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RiskPredictionService(DeviceRepository deviceRepository,
                                 LightPointRepository lightPointRepository,
                                 AlarmRepository alarmRepository,
                                 AgentActionAuditRepository auditRepository,
                                 DeviceHealthReportRepository healthReportRepository,
                                 DeviceRiskPredictionRepository predictionRepository) {
        this.deviceRepository = deviceRepository;
        this.lightPointRepository = lightPointRepository;
        this.alarmRepository = alarmRepository;
        this.auditRepository = auditRepository;
        this.healthReportRepository = healthReportRepository;
        this.predictionRepository = predictionRepository;
    }

    // ==================== 对外入口 ====================

    /** 全量预测：供定时任务与手动"全部预测"调用，返回成功生成报告的设备数 */
    @Transactional
    public int predictAll() {
        List<Device> devices = deviceRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        long windowStart = System.currentTimeMillis() - WINDOW_7D_MS;

        // 第一遍：为每台设备提取基础特征（1-6），并收集群体统计（用于特征 7）
        List<DeviceAnalysis> analyses = new ArrayList<>();
        for (Device device : devices) {
            analyses.add(analyze(device, windowStart));
        }
        List<Double> fleetTemps = new ArrayList<>();
        List<Double> fleetCurrents = new ArrayList<>();
        for (DeviceAnalysis a : analyses) {
            if (a.meanTempOn != null) fleetTemps.add(a.meanTempOn);
            if (a.meanCurrentOn != null) fleetCurrents.add(a.meanCurrentOn);
        }
        Double medianTemp = median(fleetTemps);
        Double medianCurrent = median(fleetCurrents);

        // 第二遍：补充横向偏差特征并聚合保存
        int saved = 0;
        for (DeviceAnalysis a : analyses) {
            Feature fleetFeature = buildFleetFeature(a, medianTemp, medianCurrent, analyses.size());
            DeviceRiskPrediction report = aggregateAndSave(a, fleetFeature, now);
            if (report != null) saved++;
        }
        cleanupOld();
        log.info("风险预测完成：共 {} 台设备，生成 {} 份报告", devices.size(), saved);
        return saved;
    }

    /** 单设备预测（手动"立即预测"），设备不存在或数据不足时返回 null */
    @Transactional
    public DeviceRiskPrediction predictOne(String deviceCode) {
        Device device = deviceRepository.findByCode(deviceCode).orElse(null);
        if (device == null) return null;
        long windowStart = System.currentTimeMillis() - WINDOW_7D_MS;
        DeviceAnalysis a = analyze(device, windowStart);

        // 群体 = 全体设备（含本机）：逐台统计 7 天均值，取中位数
        List<Double> fleetTemps = new ArrayList<>();
        List<Double> fleetCurrents = new ArrayList<>();
        int fleetSize = 0;
        for (Device other : deviceRepository.findAll()) {
            DeviceAnalysis oa = analyze(other, windowStart);
            fleetSize++;
            if (oa.meanTempOn != null) fleetTemps.add(oa.meanTempOn);
            if (oa.meanCurrentOn != null) fleetCurrents.add(oa.meanCurrentOn);
        }
        Feature fleetFeature = buildFleetFeature(a, median(fleetTemps), median(fleetCurrents), fleetSize);
        return aggregateAndSave(a, fleetFeature, LocalDateTime.now());
    }

    public List<DeviceRiskPrediction> latestForAllDevices() {
        return predictionRepository.findLatestForAllDevices();
    }

    public List<DeviceRiskPrediction> history(String deviceCode) {
        return predictionRepository.findTop30ByDeviceCodeOrderByPredictedAtDesc(deviceCode);
    }

    @Transactional
    public int cleanupOld() {
        return predictionRepository.deleteOlderThan(LocalDateTime.now().minusDays(retentionDays));
    }

    // ==================== 特征提取 ====================

    /** 单设备的滑动窗口序列与基础特征（1-6） */
    private DeviceAnalysis analyze(Device device, long windowStart) {
        DeviceAnalysis a = new DeviceAnalysis();
        a.device = device;
        String code = device.getCode();
        List<LightPoint> points = lightPointRepository.findByDeviceCodeAndTsBetweenOrderByTsAsc(
                code, windowStart, System.currentTimeMillis());

        List<double[]> voltSeries = new ArrayList<>();
        List<double[]> tempOnSeries = new ArrayList<>();
        List<double[]> tempAllSeries = new ArrayList<>();
        List<double[]> currOnSeries = new ArrayList<>();
        long bothFieldsSamples = 0;
        long contradictionSamples = 0;

        for (LightPoint p : points) {
            boolean on = "ON".equalsIgnoreCase(p.getLampStatus());
            if (p.getVoltage() != null && p.getVoltage() > 0) {
                voltSeries.add(new double[]{p.getTs(), p.getVoltage()});
            }
            if (p.getTemperature() != null) {
                tempAllSeries.add(new double[]{p.getTs(), p.getTemperature()});
                if (on) tempOnSeries.add(new double[]{p.getTs(), p.getTemperature()});
            }
            if (on && p.getCurrent() != null && p.getCurrent() > 0) {
                currOnSeries.add(new double[]{p.getTs(), p.getCurrent()});
            }
            if (p.getPower() != null && p.getLampStatus() != null) {
                bothFieldsSamples++;
                if (p.getPower() > 0.5 && "OFF".equalsIgnoreCase(p.getLampStatus())) {
                    contradictionSamples++;
                }
            }
        }

        a.featureMap.put("voltage", buildVoltageFeature(voltSeries));
        a.featureMap.put("current", buildCurrentFeature(currOnSeries));
        a.featureMap.put("temperature",
                buildTemperatureFeature(a, tempOnSeries.size() >= MIN_SERIES_SAMPLES ? tempOnSeries : tempAllSeries));
        a.featureMap.put("contradiction", buildContradictionFeature(contradictionSamples, bothFieldsSamples));
        a.featureMap.put("offline", buildOfflineFeature(code));
        a.featureMap.put("command", buildCommandFeature(code));

        a.meanTempOn = mean(values(tempOnSeries));
        a.meanCurrentOn = mean(values(currOnSeries));
        return a;
    }

    /** 特征 1：电压波动率（变异系数 CV） */
    private Feature buildVoltageFeature(List<double[]> series) {
        String key = "voltage", label = "电压波动率";
        if (series.size() < MIN_SERIES_SAMPLES) {
            return insufficient(key, label, series.size(), "遥测样本不足，无法评估电压波动");
        }
        double[] v = values(series);
        double mean = mean(v);
        double std = std(v, mean);
        double cv = mean > 0 ? std / mean : 0;
        double risk = clamp(cv / voltageCvMax);
        String detail = String.format("7 天电压均值 %.1fV，波动率 CV=%.1f%%（风险线 %.0f%%）",
                mean, cv * 100, voltageCvMax * 100);
        return new Feature(key, label, round(cv), "CV", risk, weightVoltage, detail, series.size());
    }

    /** 特征 2：电流偏移率（前半窗 vs 后半窗均值漂移，仅灯亮样本） */
    private Feature buildCurrentFeature(List<double[]> series) {
        String key = "current", label = "电流偏移率";
        if (series.size() < MIN_SERIES_SAMPLES) {
            return insufficient(key, label, series.size(), "灯亮样本不足，无法评估电流漂移");
        }
        int half = series.size() / 2;
        double mean1 = mean(values(series.subList(0, half)));
        double mean2 = mean(values(series.subList(half, series.size())));
        double drift = mean1 > 0 ? (mean2 - mean1) / mean1 : 0;
        // 正漂移（电流增大）视为老化风险；负漂移计入明细但不计风险
        double risk = clamp(Math.max(drift, 0) / currentDriftMax);
        String detail = String.format("灯亮电流由 %.2fA 漂移至 %.2fA（%+.1f%%，风险线 +%.0f%%）",
                mean1, mean2, drift * 100, currentDriftMax * 100);
        return new Feature(key, label, round(drift), "", risk, weightCurrent, detail, series.size());
    }

    /** 特征 3：温度增长趋势（最小二乘斜率 + 7 天外推，外推值暂存用于一票升级） */
    private Feature buildTemperatureFeature(DeviceAnalysis a, List<double[]> series) {
        String key = "temperature", label = "温度增长趋势";
        if (series.size() < MIN_SERIES_SAMPLES) {
            a.extrapolatedTemp = null;
            return insufficient(key, label, series.size(), "遥测样本不足，无法评估温度趋势");
        }
        long t0 = (long) series.get(0)[0];
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        int n = series.size();
        for (double[] p : series) {
            double x = (p[0] - t0) / 86400000.0; // 天
            double y = p[1];
            sumX += x; sumY += y; sumXY += x * y; sumXX += x * x;
        }
        double denom = n * sumXX - sumX * sumX;
        double slope = denom != 0 ? (n * sumXY - sumX * sumY) / denom : 0;
        double last = series.get(n - 1)[1];
        double extrapolated = last + slope * 7;
        a.extrapolatedTemp = extrapolated;
        double risk = clamp((extrapolated - tempWarn) / (tempAlarm - tempWarn));
        String detail = String.format("温度斜率 %+.2f℃/天，外推 7 天后 %.1f℃（告警线 %.0f℃）",
                slope, extrapolated, tempAlarm);
        return new Feature(key, label, round(slope), "℃/天", risk, weightTemperature, detail, series.size());
    }

    /** 特征 4：功率/开关状态矛盾率（power>0.5W 但状态 OFF） */
    private Feature buildContradictionFeature(long contradictions, long samples) {
        String key = "contradiction", label = "功率/状态矛盾";
        if (samples < MIN_SERIES_SAMPLES) {
            return insufficient(key, label, (int) samples, "有效样本不足，无法评估状态矛盾");
        }
        double ratio = (double) contradictions / samples;
        double risk = clamp(ratio / contradictionRatioMax);
        String detail = String.format("%d 条回传中 %d 条矛盾（功率>0.5W 但状态 OFF），占比 %.1f%%",
                samples, contradictions, ratio * 100);
        return new Feature(key, label, round(ratio), "", risk, weightContradiction, detail, (int) samples);
    }

    /** 特征 5：离线频率（近 30 天离线告警累计次数；无记录本身就是有效观测） */
    private Feature buildOfflineFeature(String code) {
        String key = "offline", label = "离线频率";
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Alarm> alarms = alarmRepository.findByDeviceIdAndTypeOrderByTsDesc(code, "离线");
        int episodes = 0;
        for (Alarm alarm : alarms) {
            LocalDateTime occurred = alarm.getLastOccurredAt() != null ? alarm.getLastOccurredAt()
                    : alarm.getFirstOccurredAt() != null ? alarm.getFirstOccurredAt() : alarm.getCreatedAt();
            if (occurred != null && occurred.isAfter(since)) {
                episodes += alarm.getOccurrenceCount() == null ? 1 : alarm.getOccurrenceCount();
            }
        }
        double risk = clamp(episodes / offlineCountMax);
        String detail = String.format("近 30 天离线 %d 次（风险线 %d 次）", episodes, (int) offlineCountMax);
        return new Feature(key, label, (double) episodes, "次/30天", risk, weightOffline, detail, alarms.size());
    }

    /** 特征 6：控制命令失败率（近 7 天 Agent 控制审计） */
    private Feature buildCommandFeature(String code) {
        String key = "command", label = "控制命令失败率";
        Long since = System.currentTimeMillis() - WINDOW_7D_MS;
        List<AgentActionAudit> audits = auditRepository.findByTargetIdAndRequestedAtAfterOrderByRequestedAtDesc(code, since);
        int success = 0, failure = 0;
        for (AgentActionAudit audit : audits) {
            ActionStatus s = audit.getStatus();
            if (s == ActionStatus.SUCCESS) success++;
            else if (s == ActionStatus.FAILED || s == ActionStatus.COMMAND_ACCEPTED) failure++;
            // CANCELLED/EXPIRED/PENDING 等属用户与流程行为，不代表设备健康，不计入
        }
        int outcomes = success + failure;
        if (outcomes < MIN_COMMAND_OUTCOMES) {
            return insufficient(key, label, outcomes,
                    "近 7 天控制指令不足 " + MIN_COMMAND_OUTCOMES + " 次，无法评估");
        }
        double rate = (double) failure / outcomes;
        double risk = clamp(rate / commandFailureMax);
        String detail = String.format("近 7 天 %d 次控制指令 %d 次失败/未回执（%.0f%%，风险线 %.0f%%）",
                outcomes, failure, rate * 100, commandFailureMax * 100);
        return new Feature(key, label, round(rate), "", risk, weightCommand, detail, outcomes);
    }

    /** 特征 7：同群横向偏差（当前无区域字段，按拍板退化为目标设备 vs 全体设备中位数） */
    private Feature buildFleetFeature(DeviceAnalysis a, Double medianTemp, Double medianCurrent, int fleetSize) {
        String key = "fleet", label = "同群横向偏差";
        if (fleetSize < MIN_FLEET_SIZE || (a.meanTempOn == null && a.meanCurrentOn == null)) {
            return insufficient(key, label, fleetSize, "可用设备不足 " + MIN_FLEET_SIZE + " 台，无法横向对比");
        }
        double tempDev = 0, currDev = 0;
        boolean hasTemp = a.meanTempOn != null && medianTemp != null && medianTemp > 0;
        boolean hasCurr = a.meanCurrentOn != null && medianCurrent != null && medianCurrent > 0;
        if (hasTemp) tempDev = (a.meanTempOn - medianTemp) / medianTemp;
        if (hasCurr) currDev = (a.meanCurrentOn - medianCurrent) / medianCurrent;
        if (!hasTemp && !hasCurr) {
            return insufficient(key, label, fleetSize, "群体遥测数据不足，无法横向对比");
        }
        double deviation = Math.max(Math.abs(tempDev), Math.abs(currDev));
        double risk = clamp(deviation / fleetDeviationMax);
        StringBuilder detail = new StringBuilder("较全体设备中位数：");
        if (hasTemp) detail.append(String.format("温度 %+.1f%%", tempDev * 100));
        if (hasTemp && hasCurr) detail.append("，");
        if (hasCurr) detail.append(String.format("电流 %+.1f%%", currDev * 100));
        detail.append(String.format("（风险线 ±%.0f%%）", fleetDeviationMax * 100));
        return new Feature(key, label, round(deviation), "", risk, weightFleet, detail.toString(), fleetSize);
    }

    // ==================== 聚合与保存 ====================

    private DeviceRiskPrediction aggregateAndSave(DeviceAnalysis a, Feature fleetFeature, LocalDateTime now) {
        List<Feature> all = new ArrayList<>(a.featureMap.values());
        all.add(fleetFeature);

        double weightedSum = 0, weightSum = 0;
        int availableCount = 0;
        boolean tempEscalation = false;
        for (Feature f : all) {
            if (f.insufficient()) continue;
            weightedSum += f.weight() * f.risk();
            weightSum += f.weight();
            availableCount++;
            if ("temperature".equals(f.key()) && a.extrapolatedTemp != null && a.extrapolatedTemp >= tempAlarm) {
                tempEscalation = true;
            }
        }
        if (availableCount == 0 || weightSum <= 0) {
            log.info("设备 {} 特征样本均不足，跳过预测", a.device.getCode());
            return null;
        }

        double composite = clamp(weightedSum / weightSum);
        String level;
        if (tempEscalation || composite >= levelHigh) level = "HIGH";
        else if (composite >= levelMedium) level = "MEDIUM";
        else level = "LOW";

        // 主要原因 = 贡献度 Top3 且风险 > 0 的特征明细
        List<String> reasons = all.stream()
                .filter(f -> !f.insufficient() && f.risk() > 0)
                .sorted(Comparator.comparingDouble((Feature f) -> f.weight() * f.risk()).reversed())
                .limit(3)
                .map(Feature::detail)
                .toList();
        if (reasons.isEmpty()) reasons = List.of("各项特征均在正常范围内");

        Feature top = all.stream()
                .filter(f -> !f.insufficient())
                .max(Comparator.comparingDouble(f -> f.weight() * f.risk()))
                .orElse(null);
        String advice = buildAdvice(level, top);

        DeviceRiskPrediction report = new DeviceRiskPrediction();
        report.setDeviceCode(a.device.getCode());
        report.setPredictedAt(now);
        report.setRiskLevel(level);
        report.setRiskScore(round4(composite));
        report.setHorizonDays(7);
        DeviceHealthReport latestHealth =
                healthReportRepository.findFirstByDeviceCodeOrderByCreatedAtDesc(a.device.getCode());
        report.setCurrentHealthScore(latestHealth != null ? latestHealth.getHealthScore() : null);
        report.setFeatures(toJson(all));
        report.setReasons(toJson(reasons));
        report.setAdvice(advice);
        report.setCreatedAt(now);
        return predictionRepository.save(report);
    }

    /** 规则模板：主导特征 → 确定性建议（同样输入永远同样输出，可追溯） */
    private String buildAdvice(String level, Feature top) {
        if ("LOW".equals(level) || top == null) {
            return "设备运行平稳，保持例行巡检即可";
        }
        String prefix = "HIGH".equals(level) ? "未来 7 天故障风险高：" : "未来 7 天故障风险中等：";
        return switch (top.key()) {
            case "temperature" -> prefix + "温度持续攀升，建议 48 小时内断电检查驱动电源与散热片积尘";
            case "current" -> prefix + "电流持续偏移，建议对比额定电流检测 LED 驱动是否老化";
            case "voltage" -> prefix + "供电波动明显，建议检查供电线路接头与波动来源";
            case "offline" -> prefix + "离线频发，建议检查网络模块与电源接触";
            case "command" -> prefix + "控制指令多次无回执，建议检查继电器与设备通信链路";
            case "contradiction" -> prefix + "状态回传矛盾，建议检查继电器触点与上报链路";
            case "fleet" -> prefix + "指标明显偏离同批设备，建议对比正常设备排查个体差异";
            default -> prefix + "建议安排例行检查";
        };
    }

    // ==================== 数据结构 ====================

    /**
     * 特征值对象（Jackson 直接序列化为 features JSON 数组元素）。
     * insufficient=true 的特征 risk/weight 无效，聚合时跳过。
     */
    public record Feature(String key, String label, Double value, String valueUnit,
                          double risk, double weight, double riskContribution,
                          String detail, int sampleCount, boolean insufficient) {
        /** 样本充足特征的便捷构造：贡献度 = risk × weight */
        Feature(String key, String label, Double value, String valueUnit,
                double risk, double weight, String detail, int sampleCount) {
            this(key, label, value, valueUnit, risk, weight, round4(risk * weight),
                    detail, sampleCount, false);
        }
    }

    private static Feature insufficient(String key, String label, int sampleCount, String detail) {
        return new Feature(key, label, null, null, 0, 0, 0, detail, sampleCount, true);
    }

    private static class DeviceAnalysis {
        Device device;
        final Map<String, Feature> featureMap = new LinkedHashMap<>();
        Double meanTempOn;
        Double meanCurrentOn;
        Double extrapolatedTemp; // 温度 7 天外推值，null=无法外推（供一票升级规则）
    }

    // ==================== 数学工具 ====================

    private static double[] values(List<double[]> series) {
        double[] v = new double[series.size()];
        for (int i = 0; i < series.size(); i++) v[i] = series.get(i)[1];
        return v;
    }

    private static double mean(double[] v) {
        if (v.length == 0) return 0;
        double s = 0;
        for (double x : v) s += x;
        return s / v.length;
    }

    private static double std(double[] v, double mean) {
        if (v.length <= 1) return 0;
        double s = 0;
        for (double x : v) s += (x - mean) * (x - mean);
        return Math.sqrt(s / v.length);
    }

    private static Double median(List<Double> list) {
        if (list.isEmpty()) return null;
        List<Double> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double clamp(double v) {
        if (Double.isNaN(v) || v < 0) return 0;
        return Math.min(v, 1);
    }

    private static Double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        return Math.round(v * 10000) / 10000.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("特征 JSON 序列化失败", e);
            return "[]";
        }
    }
}
