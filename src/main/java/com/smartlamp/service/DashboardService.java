package com.smartlamp.service;

import com.smartlamp.entity.enums.DeviceStatus;
import com.smartlamp.dto.DashboardOverviewDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.SummaryDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.LightPointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private LightPointRepository lightPointRepository;

    // 原有的大屏概览
    public DashboardOverviewDTO getOverview() {
        List<Device> devices = deviceRepository.findAll();
        long total = devices.size();
        long online = devices.stream().filter(d -> DeviceStatus.ONLINE.equals(d.getStatus())).count();
        long offline = devices.stream().filter(d -> DeviceStatus.OFFLINE.equals(d.getStatus())).count();
        double avgLux = devices.stream()
                .filter(d -> d.getLatestLux() != null)
                .mapToDouble(Device::getLatestLux)
                .average()
                .orElse(0.0);
        return new DashboardOverviewDTO(total, online, offline, avgLux);
    }

    // 新增：汇总（兼容旧 /api/summary）
    public SummaryDTO getSummary() {
        DashboardOverviewDTO overview = getOverview();

        // 获取最近一条光照数据（任意设备）
        Pageable pageable = PageRequest.of(0, 1);
        List<LightPoint> recentPoints = lightPointRepository.findAllByOrderByTsDesc(pageable);
        LightDataDTO lastTelemetry = null;
        if (!recentPoints.isEmpty()) {
            LightPoint point = recentPoints.get(0);
            lastTelemetry = new LightDataDTO(point.getDeviceCode(), point.getLux(), point.getTs());
        }

        return new SummaryDTO(
                overview.getTotalDevices(),
                overview.getOnlineCount(),
                overview.getOfflineCount(),
                overview.getAvgLux(),
                lastTelemetry
        );
    }
}