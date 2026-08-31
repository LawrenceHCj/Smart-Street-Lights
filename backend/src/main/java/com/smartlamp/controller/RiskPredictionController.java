package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.entity.DeviceRiskPrediction;
import com.smartlamp.service.DemoDataSeedService;
import com.smartlamp.service.RiskPredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预测性维护接口（设备未来 7 天故障风险）。
 * 查询：登录即可（municipal 只读）；触发预测/生成演示数据：admin 或 operator。
 */
@RestController
@RequestMapping("/api/risk")
public class RiskPredictionController {

    @Autowired
    private RiskPredictionService riskPredictionService;

    @Autowired
    private DemoDataSeedService demoDataSeedService;

    // GET /api/risk/latest —— 全部设备的最新一次预测
    @GetMapping("/latest")
    public ApiResponse<List<DeviceRiskPrediction>> latest() {
        return ApiResponse.success(riskPredictionService.latestForAllDevices());
    }

    // GET /api/risk/history/{deviceCode} —— 单设备预测历史（最多 30 条）
    @GetMapping("/history/{deviceCode}")
    public ApiResponse<List<DeviceRiskPrediction>> history(@PathVariable String deviceCode) {
        return ApiResponse.success(riskPredictionService.history(deviceCode));
    }

    // POST /api/risk/predict/{deviceCode} —— 立即预测单台设备
    @PostMapping("/predict/{deviceCode}")
    @PreAuthorize("hasAnyRole('admin','operator')")
    public ApiResponse<DeviceRiskPrediction> predictOne(@PathVariable String deviceCode) {
        DeviceRiskPrediction report = riskPredictionService.predictOne(deviceCode);
        if (report == null) {
            return ApiResponse.error(400, "设备不存在或遥测样本不足，无法预测");
        }
        return ApiResponse.success(report);
    }

    // POST /api/risk/predict-all —— 立即预测全部设备（定时任务之外的演示入口）
    @PostMapping("/predict-all")
    @PreAuthorize("hasAnyRole('admin','operator')")
    public ApiResponse<Map<String, Object>> predictAll() {
        int saved = riskPredictionService.predictAll();
        return ApiResponse.success(Map.of("predictedDevices", saved));
    }

    // POST /api/risk/seed-demo —— 生成演示数据（4 台虚拟设备 + 7 天合成遥测）
    @PostMapping("/seed-demo")
    @PreAuthorize("hasAnyRole('admin','operator')")
    public ApiResponse<String> seedDemo() {
        return ApiResponse.success(demoDataSeedService.seed());
    }
}
