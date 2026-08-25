package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.AddDeviceRequest;
import com.smartlamp.dto.DeviceControlRequest;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.SwitchLightRequest;
import com.smartlamp.dto.UpdateDeviceRequest;
import com.smartlamp.entity.Device;
import com.smartlamp.service.DeviceService;
import com.smartlamp.service.MqttPublisherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ApiResponse<Void> switchLight(@PathVariable String deviceId,
                                         @RequestBody SwitchLightRequest request) {
        if (deviceService.getDeviceByCode(deviceId) == null) {
            return ApiResponse.error(400, "设备不存在");
        }
        String payload = "{\"deviceId\":\"" + deviceId + "\",\"on\":" + request.isOn() + "}";
        String topic = "device/" + deviceId + "/cmd";
        mqttPublisherService.publish(topic, payload);
        return ApiResponse.success(null);
    }

    // POST /api/devices/{deviceId}/control —— 兼容旧契约
    @PostMapping("/{deviceId}/control")
    public ApiResponse<Void> control(@PathVariable String deviceId,
                                     @RequestBody DeviceControlRequest request) {
        if (deviceService.getDeviceByCode(deviceId) == null) {
            return ApiResponse.error(400, "设备不存在");
        }

        String action = request.getAction();
        if (action == null || !action.matches("ON|OFF")) {
            return ApiResponse.error(400, "action 必须为 ON 或 OFF");
        }

        boolean on = "ON".equals(action);
        String payload = "{\"deviceId\":\"" + deviceId + "\",\"on\":" + on + "}";
        String topic = "device/" + deviceId + "/cmd";
        mqttPublisherService.publish(topic, payload);

        // 更新设备开关状态
        Device device = deviceService.getDeviceByCode(deviceId);
        if (device != null) {
            device.setLightOn(on);
            deviceService.saveDevice(device);   // 确保 DeviceService 中有 saveDevice 方法
        }

        return ApiResponse.success(null);
    }

    // POST /api/devices 添加设备
    @PostMapping
    public ApiResponse<DeviceDTO> addDevice(@RequestBody AddDeviceRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            return ApiResponse.error(400, "设备编号不能为空");
        }
        if (request.getLongitude() == null || request.getLatitude() == null ||
                request.getLongitude() < -180 || request.getLongitude() > 180 ||
                request.getLatitude() < -90 || request.getLatitude() > 90) {
            return ApiResponse.error(400, "请输入有效经纬度：经度 -180 至 180，纬度 -90 至 90");
        }

        DeviceDTO dto = deviceService.addDevice(request);
        if (dto == null) {
            return ApiResponse.error(400, "设备编号已存在");
        }
        return ApiResponse.success(dto);
    }

    // DELETE /api/devices/{deviceId} 解绑设备
    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> removeDevice(@PathVariable String deviceId) {
        boolean success = deviceService.removeDevice(deviceId);
        if (!success) {
            return ApiResponse.error(400, "设备不存在");
        }
        return ApiResponse.success(null);
    }

    // PATCH /api/devices/{deviceId} 更新设备
    @PatchMapping("/{deviceId}")
    public ApiResponse<DeviceDTO> updateDevice(@PathVariable String deviceId,
                                               @RequestBody UpdateDeviceRequest request) {
        // 参数校验：经纬度如果提供，必须在有效范围
        if (request.getLongitude() != null && (request.getLongitude() < -180 || request.getLongitude() > 180)) {
            return ApiResponse.error(400, "经度必须在 -180 至 180 之间");
        }
        if (request.getLatitude() != null && (request.getLatitude() < -90 || request.getLatitude() > 90)) {
            return ApiResponse.error(400, "纬度必须在 -90 至 90 之间");
        }

        DeviceDTO dto = deviceService.updateDevice(deviceId, request);
        if (dto == null) {
            return ApiResponse.error(400, "设备不存在");
        }
        return ApiResponse.success(dto);
    }
}