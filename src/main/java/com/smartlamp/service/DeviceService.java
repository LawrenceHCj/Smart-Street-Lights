package com.smartlamp.service;

import com.smartlamp.entity.enums.DeviceStatus;
import com.smartlamp.dto.AddDeviceRequest;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.UpdateDeviceRequest;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.AlarmRepository;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.LightPointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private LightPointRepository lightPointRepository;

    @Autowired
    private AlarmRepository alarmRepository;

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
        if (deviceRepository.findByCode(request.getCode()).isPresent()) {
            return null;
        }
        Device device = new Device();
        device.setCode(request.getCode());
        device.setLocation(request.getLocation());
        device.setLongitude(request.getLongitude());
        device.setLatitude(request.getLatitude());
        device.setStatus(DeviceStatus.OFFLINE);
        device.setLatestLux(null);
        device.setLastSeen(null);
        device.setLightOn(false);
        device.setCreatedAt(LocalDateTime.now());
        deviceRepository.save(device);
        return convertToDTO(device);
    }

    // 更新设备信息
    public DeviceDTO updateDevice(String code, UpdateDeviceRequest request) {
        Device device = deviceRepository.findByCode(code).orElse(null);
        if (device == null) {
            return null;
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

    // 保存设备
    public void saveDevice(Device device) {
        deviceRepository.save(device);
    }

    // 解绑设备（级联删除光照历史和告警）
    @Transactional
    public boolean removeDevice(String code) {
        Device device = deviceRepository.findByCode(code).orElse(null);
        if (device == null) {
            return false;
        }
        // 先删除该设备的光照历史
        lightPointRepository.deleteByDeviceCode(code);
        // 再删除该设备的告警记录
        alarmRepository.deleteByDeviceId(code);
        // 最后删除设备本身
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
        dto.setStatus(device.getStatus().name());
        dto.setLatestLux(device.getLatestLux());
        dto.setLastSeen(device.getLastSeen());
        return dto;
    }
}