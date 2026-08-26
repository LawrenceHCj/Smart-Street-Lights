package com.smartlamp.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceHealthDTO(
        Long id,
        String deviceCode,
        Integer healthScore,
        List<HealthAnomalyDTO> anomalies,
        LocalDateTime createdAt
) {
}
