package com.smartlamp.service;

import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

    // 原有的获取所有设备实体（备用）
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    // 获取所有设备 DTO（用于 /api/devices）
    public List<DeviceDTO> getAllDeviceDTOs() {
        return deviceRepository.findAll().stream()
                .map(device -> new DeviceDTO(
                        device.getId(),
                        device.getCode(),
                        device.getLocation(),
                        device.getStatus(),
                        device.getLatestLux(),
                        device.getLastSeen()
                ))
                .collect(Collectors.toList());
    }

    // 根据设备编号获取设备实体
    public Device getDeviceByCode(String code) {
        return deviceRepository.findByCode(code).orElse(null);
    }

    // 获取当前光照数据（用于 /api/devices/{deviceId}/light）
    public LightDataDTO getCurrentLight(String deviceCode) {
        Device device = deviceRepository.findByCode(deviceCode).orElse(null);
        if (device == null) {
            return null;
        }
        return new LightDataDTO(device.getCode(), device.getLatestLux(), device.getLastSeen());
    }

    // 添加设备
    public boolean addDevice(String code, String location) {
        // 检查设备编号是否已存在
        if (deviceRepository.findByCode(code).isPresent()) {
            return false; // 已存在
        }
        Device device = new Device();
        device.setCode(code);
        device.setLocation(location);
        device.setStatus("OFFLINE"); // 初始状态离线，等待心跳
        device.setLatestLux(null);
        device.setLastSeen(null);
        device.setCreatedAt(LocalDateTime.now());
        deviceRepository.save(device);
        return true;
    }

    // 解绑设备
    public boolean removeDevice(String code) {
        Device device = deviceRepository.findByCode(code).orElse(null);
        if (device == null) {
            return false; // 设备不存在
        }
        deviceRepository.delete(device);
        return true;
    }
}