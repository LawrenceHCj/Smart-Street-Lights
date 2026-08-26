package com.smartlamp.controller;

import com.smartlamp.dto.AddDeviceRequest;
import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.ControlRequest;
import com.smartlamp.dto.ControlResultDTO;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.SwitchLightRequest;
import com.smartlamp.dto.UpdateDeviceRequest;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.service.DeviceCommandService;
import com.smartlamp.service.DeviceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;
    private final DeviceCommandService commandService;

    public DeviceController(DeviceService deviceService, DeviceCommandService commandService) {
        this.deviceService = deviceService;
        this.commandService = commandService;
    }

    @GetMapping
    public ApiResponse<List<DeviceDTO>> listDevices() {
        return ApiResponse.success(deviceService.getAllDeviceDTOs());
    }

    @GetMapping("/{deviceId}/light")
    public ApiResponse<LightDataDTO> getLight(@PathVariable String deviceId) {
        LightDataDTO light = deviceService.getCurrentLight(deviceId);
        return light == null ? ApiResponse.error(400, "设备不存在") : ApiResponse.success(light);
    }

    @PostMapping("/{deviceId}/switch")
    public ApiResponse<ControlResultDTO> switchLight(@PathVariable String deviceId,
                                                      @RequestBody SwitchLightRequest request) {
        DeviceCommand command = commandService.dispatch(deviceId, request.isOn() ? "ON" : "OFF", "MANUAL");
        return ApiResponse.success(commandService.toResult(command, "MANUAL"));
    }

    @PostMapping
    public ApiResponse<DeviceDTO> addDevice(@RequestBody AddDeviceRequest request) {
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
        return device == null
                ? ApiResponse.error(400, "设备编号已存在")
                : ApiResponse.success(deviceService.toDTO(device));
    }

    @PatchMapping("/{deviceId}")
    public ApiResponse<DeviceDTO> updateDevice(@PathVariable String deviceId,
                                                @RequestBody UpdateDeviceRequest request) {
        if ((request.getLongitude() != null && (request.getLongitude() < -180 || request.getLongitude() > 180))
                || (request.getLatitude() != null && (request.getLatitude() < -90 || request.getLatitude() > 90))) {
            return ApiResponse.error(400, "经纬度超出有效范围");
        }
        Device device = deviceService.updateDevice(deviceId, request.getName(), request.getLocation(), request.getBinding(),
                request.getBound(), request.getLongitude(), request.getLatitude());
        return device == null
                ? ApiResponse.error(404, "device not found")
                : ApiResponse.success(deviceService.toDTO(device));
    }

    @PostMapping("/{deviceId}/control")
    public ApiResponse<ControlResultDTO> control(@PathVariable String deviceId,
                                                  @RequestBody ControlRequest request) {
        DeviceCommand command = commandService.dispatch(deviceId, request.getAction(), "MANUAL");
        return ApiResponse.success(commandService.toResult(command, "MANUAL"));
    }

    @GetMapping("/commands/{commandId}")
    public ApiResponse<ControlResultDTO> getCommandStatus(@PathVariable String commandId) {
        return ApiResponse.success(commandService.toResult(commandService.find(commandId), "MANUAL"));
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> removeDevice(@PathVariable String deviceId) {
        return deviceService.removeDevice(deviceId)
                ? ApiResponse.success(null)
                : ApiResponse.error(400, "设备不存在");
    }
}
