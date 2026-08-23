package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.AddDeviceRequest;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.SwitchLightRequest;
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

    // POST /api/devices 添加设备
    @PostMapping
    public ApiResponse<Void> addDevice(@RequestBody AddDeviceRequest request) {
        // 参数校验
        if (request.getCode() == null || request.getCode().isBlank()) {
            return ApiResponse.error(400, "设备编号不能为空");
        }
        boolean success = deviceService.addDevice(request.getCode(), request.getLocation());
        if (!success) {
            return ApiResponse.error(400, "设备编号已存在");
        }
        return ApiResponse.success(null);
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
}