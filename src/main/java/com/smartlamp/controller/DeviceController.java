package com.smartlamp.controller;

import com.smartlamp.dto.*;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.service.DeviceCommandService;
import com.smartlamp.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceCommandService deviceCommandService;

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
    public ApiResponse<String> switchLight(@PathVariable String deviceId,
                                           @RequestBody SwitchLightRequest request) {
        try {
            String commandId = deviceCommandService.dispatchCommand(deviceId, request.isOn() ? "ON" : "OFF");
            return ApiResponse.success(commandId);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // POST /api/devices/{deviceId}/control —— 兼容旧契约
    @PostMapping("/{deviceId}/control")
    public ApiResponse<String> control(@PathVariable String deviceId,
                                       @RequestBody DeviceControlRequest request) {
        try {
            String action = request.getAction();
            if (action == null || !action.matches("ON|OFF")) {
                return ApiResponse.error(400, "action 必须为 ON 或 OFF");
            }
            String commandId = deviceCommandService.dispatchCommand(deviceId, action);
            return ApiResponse.success(commandId);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // GET /api/devices/commands/{commandId}
    @GetMapping("/commands/{commandId}")
    public ApiResponse<DeviceCommand> getCommandStatus(@PathVariable String commandId) {
        DeviceCommand command = deviceCommandService.getCommandStatus(commandId);
        if (command == null) {
            return ApiResponse.error(400, "指令不存在");
        }
        return ApiResponse.success(command);
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

    // PATCH /api/devices/{deviceId} 更新设备
    @PatchMapping("/{deviceId}")
    public ApiResponse<DeviceDTO> updateDevice(@PathVariable String deviceId,
                                               @RequestBody UpdateDeviceRequest request) {
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