package com.smartlamp.service;

import com.smartlamp.dto.AddDeviceRequest;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.UpdateDeviceRequest;
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

    // 获取所有设备实体（备用）
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    // 获取所有设备 DTO（用于 /api/devices）
    public List<DeviceDTO> getAllDeviceDTOs() {
        return deviceRepository.findAll().stream()
                .map(this::convertToDTO)
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

    // 添加设备，返回 DeviceDTO
    public DeviceDTO addDevice(AddDeviceRequest request) {
        // 检查设备编号是否已存在
        if (deviceRepository.findByCode(request.getCode()).isPresent()) {
            return null; // 已存在
        }
        Device device = new Device();
        device.setCode(request.getCode());
        device.setLocation(request.getLocation());
        device.setLongitude(request.getLongitude());
        device.setLatitude(request.getLatitude());
        device.setStatus("OFFLINE"); // 初始状态离线，等待心跳
        device.setLatestLux(null);
        device.setLastSeen(null);
        device.setLightOn(false);
        device.setCreatedAt(LocalDateTime.now());
        deviceRepository.save(device);
        return convertToDTO(device);
    }

    // 更新设备信息（部分更新）
    public DeviceDTO updateDevice(String code, UpdateDeviceRequest request) {
        Device device = deviceRepository.findByCode(code).orElse(null);
        if (device == null) {
            return null; // 设备不存在
        }

        if (request.getLocation() != null) {
            device.setLocation(request.getLocation());
        }
        if (request.getLongitude() != null) {
            device.setLongitude(request.getLongitude());
        }
        if (request.getLatitude() != null) {
            device.setLatitude(request.getLatitude());
        }

        deviceRepository.save(device);
        return convertToDTO(device);
    }

    // 保存设备（用于更新状态）
    public void saveDevice(Device device) {
        deviceRepository.save(device);
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

    // 实体转 DTO
    private DeviceDTO convertToDTO(Device device) {
        DeviceDTO dto = new DeviceDTO();
        dto.setId(device.getId());
        dto.setCode(device.getCode());
        dto.setLocation(device.getLocation());
        dto.setLongitude(device.getLongitude());
        dto.setLatitude(device.getLatitude());
        dto.setStatus(device.getStatus());
        dto.setLatestLux(device.getLatestLux());
        dto.setLastSeen(device.getLastSeen());
        return dto;
    }
}