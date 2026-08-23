package com.smartlamp.service;

import com.smartlamp.dto.TelemetryDTO;
import com.smartlamp.entity.LightPoint;
import com.smartlamp.repository.LightPointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TelemetryService {
    @Autowired
    private LightPointRepository repository;

    public List<TelemetryDTO> list(String deviceId, int limit) {
        List<LightPoint> points = deviceId == null || deviceId.isBlank()
                ? repository.findAllByOrderByTsDesc(PageRequest.of(0, limit))
                : repository.findByDeviceCodeOrderByTsDesc(deviceId, PageRequest.of(0, limit));
        return points.stream()
                .map(point -> new TelemetryDTO(point.getId(), point.getDeviceCode(), point.getLux(), null,
                        point.getTs(), "MQTT"))
                .toList();
    }
}
