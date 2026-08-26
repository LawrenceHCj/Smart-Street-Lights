package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.AddDeviceRequest;
import com.smartlamp.dto.CommandStatus;
import com.smartlamp.dto.ControlOutcome;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.SwitchLightRequest;
import com.smartlamp.dto.UpdateDeviceRequest;
import com.smartlamp.dto.ControlRequest;
import com.smartlamp.dto.ControlResultDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.service.DeviceControlService;
import com.smartlamp.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    // 开关灯统一走 DeviceControlService（阶段18：网页按钮与 Agent 复用同一控制入口）
    @Autowired
    private DeviceControlService deviceControlService;

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

    // POST /api/devices/{deviceId}/switch（payload 已统一为 {"action":"ON|OFF"} 格式，与 /control 一致）
    @PostMapping("/{deviceId}/switch")
    public ApiResponse<Void> switchLight(@PathVariable String deviceId,
                                         @RequestBody SwitchLightRequest request) {
        ControlOutcome outcome = request.isOn()
                ? deviceControlService.turnOnLight(deviceId)
                : deviceControlService.turnOffLight(deviceId);
        if (outcome.getStatus() == CommandStatus.FAILED || outcome.getStatus() == CommandStatus.TIMEOUT) {
            return ApiResponse.error(400, outcome.getMessage());
        }
        return ApiResponse.success(null);
    }

    // POST /api/devices 添加设备
    @PostMapping
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
    public ApiResponse<ControlResultDTO> control(@PathVariable String deviceId, @RequestBody ControlRequest request) {
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase();
        if (!"ON".equals(action) && !"OFF".equals(action)) return ApiResponse.error(400, "action must be ON or OFF");
        ControlOutcome outcome = "ON".equals(action)
                ? deviceControlService.turnOnLight(deviceId)
                : deviceControlService.turnOffLight(deviceId);
        if (outcome.getStatus() == CommandStatus.FAILED || outcome.getStatus() == CommandStatus.TIMEOUT) {
            return ApiResponse.error(400, outcome.getMessage());
        }
        return ApiResponse.success(new ControlResultDTO(outcome.getCommandId(), deviceId, action,
                "MANUAL", "DISPATCHED", outcome.getIssuedAt(), "控制命令已发送"));
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
