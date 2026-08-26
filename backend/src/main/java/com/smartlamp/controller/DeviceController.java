package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.AddDeviceRequest;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.SwitchLightRequest;
import com.smartlamp.dto.UpdateDeviceRequest;
import com.smartlamp.dto.ControlRequest;
import com.smartlamp.dto.ControlResultDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.service.DeviceService;
import com.smartlamp.service.MqttPublisherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    // GET /api/devices
    @GetMapping
    public ApiResponse<List<DeviceDTO>> listDevices() {
        return ApiResponse.success(deviceService.getAllDeviceDTOs());
    }

    // GET /api/devices/{deviceId}/light
    @GetMapping("/{deviceId}/light")
    public ApiResponse<LightDataDTO> getLight(@PathVariable String deviceId) {
        LightDataDTO light = deviceService.getCurrentLight(deviceId);
        if (light == null) {
            return ApiResponse.error(400, "设备不存在");
        }
        return ApiResponse.success(light);
    }

    // POST /api/devices/{deviceId}/switch
    @PostMapping("/{deviceId}/switch")
    @PreAuthorize("hasAnyRole('admin','operator')")
    public ApiResponse<Void> switchLight(@PathVariable String deviceId,
                                         @RequestBody SwitchLightRequest request) {
        if (deviceService.getDeviceByCode(deviceId) == null) {
            return ApiResponse.error(400, "设备不存在");
        }
        String payload = "{\"deviceId\":\"" + deviceId + "\",\"on\":" + request.isOn() + "}";
        String topic = "device/" + deviceId + "/cmd";
        mqttPublisherService.publish(topic, payload);
        deviceService.updateLampStatus(deviceService.getDeviceByCode(deviceId), request.isOn() ? "ON" : "OFF");
        return ApiResponse.success(null);
    }

    // POST /api/devices 添加设备
    @PostMapping
    @PreAuthorize("hasAnyRole('admin','operator')")
    public ApiResponse<DeviceDTO> addDevice(@RequestBody AddDeviceRequest request) {
        // 参数校验
        if (request.getCode() == null || request.getCode().isBlank()) {
            return ApiResponse.error(400, "设备编号不能为空");
        }
        if (request.getLongitude() == null || request.getLatitude() == null
                || request.getLongitude() < -180 || request.getLongitude() > 180
                || request.getLatitude() < -90 || request.getLatitude() > 90) {
            return ApiResponse.error(400, "请输入有效经纬度：经度 -180 至 180，纬度 -90 至 90");
        }
        Device device = deviceService.addDevice(request.getCode(), request.getName(), request.getLocation(), request.getBinding(),
                request.getLongitude(), request.getLatitude());
        if (device == null) {
            return ApiResponse.error(400, "设备编号已存在");
        }
        return ApiResponse.success(deviceService.toDTO(device));
    }

    @PatchMapping("/{deviceId}")
    @PreAuthorize("hasAnyRole('admin','operator')")
    public ApiResponse<DeviceDTO> updateDevice(@PathVariable String deviceId, @RequestBody UpdateDeviceRequest request) {
        if ((request.getLongitude() != null && (request.getLongitude() < -180 || request.getLongitude() > 180))
                || (request.getLatitude() != null && (request.getLatitude() < -90 || request.getLatitude() > 90))) {
            return ApiResponse.error(400, "经纬度超出有效范围");
        }
        Device device = deviceService.updateDevice(deviceId, request.getName(), request.getLocation(), request.getBinding(), request.getBound(),
                request.getLongitude(), request.getLatitude());
        if (device == null) return ApiResponse.error(404, "device not found");
        return ApiResponse.success(deviceService.toDTO(device));
    }

    @PostMapping("/{deviceId}/control")
    @PreAuthorize("hasAnyRole('admin','operator')")
    public ApiResponse<ControlResultDTO> control(@PathVariable String deviceId, @RequestBody ControlRequest request) {
        Device device = deviceService.getDeviceByCode(deviceId);
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase();
        if (device == null) return ApiResponse.error(404, "device not found");
        if (!Boolean.TRUE.equals(device.getBound())) return ApiResponse.error(400, "device is unbound");
        if (!"ON".equals(action) && !"OFF".equals(action)) return ApiResponse.error(400, "action must be ON or OFF");
        mqttPublisherService.publish("device/" + deviceId + "/cmd", "{\"deviceId\":\"" + deviceId + "\",\"action\":\"" + action + "\"}");
        deviceService.updateLampStatus(device, action);
        long now = System.currentTimeMillis();
        return ApiResponse.success(new ControlResultDTO("CMD-" + now + "-" + UUID.randomUUID().toString().substring(0, 8), deviceId,
                action, "MANUAL", "DISPATCHED", now, "控制命令已发送"));
    }

    // DELETE /api/devices/{deviceId} 解绑设备
    @DeleteMapping("/{deviceId}")
    @PreAuthorize("hasAnyRole('admin','operator')")
    public ApiResponse<Void> removeDevice(@PathVariable String deviceId) {
        boolean success = deviceService.removeDevice(deviceId);
        if (!success) {
            return ApiResponse.error(400, "设备不存在");
        }
        return ApiResponse.success(null);
    }
}
