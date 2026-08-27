package com.smartlamp.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceHealthDTO(
        Long id,
        String deviceCode,
        Integer healthScore,
        HealthTelemetryDTO telemetry,
        List<HealthAnomalyDTO> anomalies,
        LocalDateTime createdAt
) {
}
