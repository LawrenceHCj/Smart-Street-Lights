package com.smartlamp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 设备故障风险预测报告（预测性维护）。
 *
 * 与 DeviceHealthReport 的分工：
 *   DeviceHealthReport  回答"设备现在健康吗"（实时快照规则评分）
 *   DeviceRiskPrediction 回答"设备未来 7 天会不会坏"（滑动窗口特征 + 加权风险模型）
 *
 * 可解释性设计（第一版不上深度学习）：
 *   features JSON 中每个特征都带 value / riskContribution / weight / detail / sampleCount，
 *   reasons 取贡献度 Top2-3 的可读原因，advice 由主导特征的规则模板生成。
 */
@Entity
@Table(name = "device_risk_prediction",
        indexes = {
                @Index(name = "idx_risk_pred_device_time", columnList = "device_code, predicted_at")
        })
public class DeviceRiskPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, length = 32)
    private String deviceCode;

    /** 预测时间 */
    @Column(name = "predicted_at", nullable = false)
    private LocalDateTime predictedAt;

    /** 风险等级：HIGH / MEDIUM / LOW */
    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    /** 综合风险分 0.0 ~ 1.0（各特征加权贡献之和） */
    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    /** 预测时间窗（天），当前固定 7 天 */
    @Column(name = "horizon_days", nullable = false)
    private Integer horizonDays;

    /** 预测时刻该设备最新健康评分（可空：设备可能尚无健康报告） */
    @Column(name = "current_health_score")
    private Integer currentHealthScore;

    /** 特征明细 JSON 数组：[{key,label,value,riskContribution,weight,detail,sampleCount,insufficient}] */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    /** 主要原因 JSON 数组（贡献度 Top2-3 的特征 detail 文本） */
    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;

    /** 规则模板生成的维护建议 */
    @Column(name = "advice", length = 1000)
    private String advice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public LocalDateTime getPredictedAt() { return predictedAt; }
    public void setPredictedAt(LocalDateTime predictedAt) { this.predictedAt = predictedAt; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public Integer getHorizonDays() { return horizonDays; }
    public void setHorizonDays(Integer horizonDays) { this.horizonDays = horizonDays; }

    public Integer getCurrentHealthScore() { return currentHealthScore; }
    public void setCurrentHealthScore(Integer currentHealthScore) { this.currentHealthScore = currentHealthScore; }

    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }

    public String getReasons() { return reasons; }
    public void setReasons(String reasons) { this.reasons = reasons; }

    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
