package com.smartlamp.service;

import com.smartlamp.dto.LightHistoryDTO;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.LightPointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LightService {

    @Autowired
    private LightPointRepository lightPointRepository;

    public LightHistoryDTO getHistory(String deviceCode, Long start, Long end) {
        List<LightPoint> points = lightPointRepository
                .findByDeviceCodeAndTsBetweenOrderByTsAsc(deviceCode, start, end);

        List<LightHistoryDTO.Point> pointDTOs = points.stream()
                .map(p -> new LightHistoryDTO.Point(p.getTs(), p.getLux()))
                .collect(Collectors.toList());

        return new LightHistoryDTO(deviceCode, pointDTOs);
    }
}