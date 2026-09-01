package com.smartlamp.service;

import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.repository.AlarmRepository;
import com.smartlamp.repository.DeviceRepository;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.DeviceHealthReportRepository;
import com.smartlamp.repository.DeviceRiskPredictionRepository;
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

    @Autowired
    private DeviceCommandRepository deviceCommandRepository;

    @Autowired
    private DeviceHealthReportRepository deviceHealthReportRepository;

    @Autowired
    private DeviceRiskPredictionRepository deviceRiskPredictionRepository;

    // 原有的获取所有设备实体（备用）
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    // 获取所有设备 DTO（用于 /api/devices）
    public List<DeviceDTO> getAllDeviceDTOs() {
        return deviceRepository.findAll().stream()
                .map(device -> {
                    DeviceDTO dto = new DeviceDTO(
                            device.getId(),
                            device.getCode(),
                            device.getLocation(),
                            device.getLongitude(),
                            device.getLatitude(),
                            device.getStatus(),
                            device.getLatestLux(),
                            device.getLastSeen()
                    );
                    // 【5号代做·需与3号对账】透出灯开关状态与绑定状态（实体已有字段）
                    dto.setLampStatus(device.getLampStatus());
                    dto.setBound(device.getBound());
                    dto.setControlMode(normalizeControlMode(device.getControlMode()));
                    return dto;
                })
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
    public Device addDevice(String code, String name, String location, String binding, Double longitude, Double latitude) {
        // 检查设备编号是否已存在
        if (deviceRepository.findByCode(code).isPresent()) {
            return null;
        }
        Device device = new Device();
        device.setCode(code);
        device.setName(name == null || name.isBlank() ? code : name.trim());
        device.setLocation(location);
        device.setLongitude(longitude);
        device.setLatitude(latitude);
        device.setBinding(binding == null ? "" : binding.trim());
        device.setBound(true);
        device.setLampStatus("OFF");
        device.setControlMode("AUTO");
        device.setStatus("OFFLINE"); // 初始状态离线，等待心跳
        device.setLatestLux(null);
        device.setLastSeen(null);
        device.setCreatedAt(LocalDateTime.now());
        return deviceRepository.save(device);
    }

    public Device updateDevice(String code, String name, String location, String binding, Boolean bound,
                               Double longitude, Double latitude) {
        Device device = getDeviceByCode(code);
        if (device == null) return null;
        if (name != null) device.setName(name.trim());
        if (location != null) device.setLocation(location.trim());
        if (binding != null) device.setBinding(binding.trim());
        if (bound != null) device.setBound(bound);
        if (longitude != null) device.setLongitude(longitude);
        if (latitude != null) device.setLatitude(latitude);
        return deviceRepository.save(device);
    }

    public DeviceDTO toDTO(Device device) {
        DeviceDTO dto = new DeviceDTO(device.getId(), device.getCode(), device.getLocation(), device.getLongitude(), device.getLatitude(), device.getStatus(),
                device.getLatestLux(), device.getLastSeen());
        dto.setLampStatus(device.getLampStatus());
        dto.setBound(device.getBound());
        dto.setControlMode(normalizeControlMode(device.getControlMode()));
        return dto;
    }

    @Transactional
    public Device setControlMode(String code, String mode) {
        Device device = getDeviceByCode(code);
        if (device == null) return null;
        device.setControlMode(normalizeRequestedMode(mode));
        return deviceRepository.save(device);
    }

    @Transactional
    public int setControlMode(List<String> codes, String mode) {
        if (codes == null || codes.isEmpty()) return 0;
        String normalized = normalizeRequestedMode(mode);
        int updated = 0;
        for (String code : codes.stream().filter(c -> c != null && !c.isBlank()).distinct().toList()) {
            Device device = getDeviceByCode(code);
            if (device == null) continue;
            device.setControlMode(normalized);
            deviceRepository.save(device);
            updated++;
        }
        return updated;
    }

    private String normalizeRequestedMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toUpperCase();
        if (!"AUTO".equals(normalized) && !"MANUAL".equals(normalized)) {
            throw new IllegalArgumentException("控制策略必须是 AUTO 或 MANUAL");
        }
        return normalized;
    }

    private String normalizeControlMode(String mode) {
        return "MANUAL".equalsIgnoreCase(mode) ? "MANUAL" : "AUTO";
    }

    public void updateLampStatus(Device device, String action) {
        device.setLampStatus(action);
        deviceRepository.save(device);
    }

    // 删除设备及其关联遥测与告警，避免遗留孤儿数据
    @Transactional
    public boolean removeDevice(String code) {
        Device device = deviceRepository.findByCode(code).orElse(null);
        if (device == null) {
            return false; // 设备不存在
        }
        lightPointRepository.deleteByDeviceCode(code);
        alarmRepository.deleteByDeviceId(code);
        deviceCommandRepository.deleteByDeviceCode(code);
        deviceHealthReportRepository.deleteByDeviceCode(code);
        deviceRiskPredictionRepository.deleteByDeviceCode(code);
        deviceRepository.delete(device);
        return true;
    }
}
