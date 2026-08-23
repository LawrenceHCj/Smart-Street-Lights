package com.smartlamp.service;

import com.smartlamp.dto.DashboardOverviewDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    @Autowired
    private DeviceRepository deviceRepository;

    public DashboardOverviewDTO getOverview() {
        List<Device> devices = deviceRepository.findAll();

        long total = devices.size();
        long online = devices.stream().filter(d -> "ONLINE".equals(d.getStatus())).count();
        long offline = devices.stream().filter(d -> "OFFLINE".equals(d.getStatus())).count();

        Double avgLux = devices.stream()
                .filter(d -> d.getLatestLux() != null)
                .mapToDouble(Device::getLatestLux)
                .average()
                .orElse(0.0);

        return new DashboardOverviewDTO(total, online, offline, avgLux);
    }
}