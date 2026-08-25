package com.smartlamp.service;

import com.smartlamp.dto.LightHistoryDTO;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.LightPointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LightService {

    @Autowired
    private LightPointRepository lightPointRepository;

    // 原有历史查询方法（不变）
    public LightHistoryDTO getHistory(String deviceCode, Long start, Long end) {
        List<LightPoint> points = lightPointRepository
                .findByDeviceCodeAndTsBetweenOrderByTsAsc(deviceCode, start, end);

        List<LightHistoryDTO.Point> pointDTOs = points.stream()
                .map(p -> new LightHistoryDTO.Point(p.getTs(), p.getLux()))
                .collect(Collectors.toList());

        return new LightHistoryDTO(deviceCode, pointDTOs);
    }

    // 新增：获取最近 N 条遥测数据
    public LightHistoryDTO getRecentTelemetry(String deviceCode, int limit) {
        if (limit <= 0) {
            limit = 10; // 默认 10 条
        }
        Pageable pageable = PageRequest.of(0, limit);
        List<LightPoint> points = lightPointRepository
                .findByDeviceCodeOrderByTsDesc(deviceCode, pageable);

        // 倒序结果需要转成升序，因为前端期望升序
        List<LightHistoryDTO.Point> pointDTOs = points.stream()
                .sorted((a, b) -> Long.compare(a.getTs(), b.getTs()))
                .map(p -> new LightHistoryDTO.Point(p.getTs(), p.getLux()))
                .collect(Collectors.toList());

        return new LightHistoryDTO(deviceCode, pointDTOs);
    }
}