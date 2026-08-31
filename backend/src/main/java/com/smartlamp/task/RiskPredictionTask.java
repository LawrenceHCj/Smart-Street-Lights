package com.smartlamp.task;

import com.smartlamp.service.RiskPredictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 预测性维护定时任务：每日 03:30 对全部设备做一次未来 7 天故障风险预测。
 * 预测需拉取每台设备 7 天遥测做特征统计，属于重查询，刻意与每小时的
 * 健康快照巡检（DeviceHealthTask）区分频率；演示可用 POST /api/risk/predict-all 手动触发。
 */
@Component
public class RiskPredictionTask {

    private static final Logger log = LoggerFactory.getLogger(RiskPredictionTask.class);

    private final RiskPredictionService riskPredictionService;

    public RiskPredictionTask(RiskPredictionService riskPredictionService) {
        this.riskPredictionService = riskPredictionService;
    }

    @Scheduled(cron = "${risk.prediction.cron:0 30 3 * * ?}")
    public void dailyPredict() {
        log.info("开始每日故障风险预测");
        try {
            int saved = riskPredictionService.predictAll();
            log.info("每日故障风险预测完成，生成 {} 份报告", saved);
        } catch (Exception e) {
            // 定时任务不允许异常外抛打断调度线程，记录后由下一次调度重试
            log.error("每日故障风险预测失败", e);
        }
    }
}
