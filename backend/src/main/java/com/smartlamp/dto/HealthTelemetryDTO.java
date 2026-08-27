package com.smartlamp.dto;

public record HealthTelemetryDTO(
        Double lux,
        Double temperature,
        Double voltage,
        Double current,
        Double power,
        Double energy,
        String lampStatus,
        Long collectedAt
) {
}
