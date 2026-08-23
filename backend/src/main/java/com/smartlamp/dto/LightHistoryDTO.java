package com.smartlamp.dto;

import lombok.Data;

import java.util.List;

@Data
public class LightHistoryDTO {
    private String deviceId;
    private List<Point> points;

    public LightHistoryDTO(String deviceId, List<Point> points) {
        this.deviceId = deviceId;
        this.points = points;
    }

    @Data
    public static class Point {
        private Long ts;
        private Double lux;

        public Point(Long ts, Double lux) {
            this.ts = ts;
            this.lux = lux;
        }
    }
}