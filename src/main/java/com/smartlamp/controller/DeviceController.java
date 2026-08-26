package com.smartlamp.controller;

import com.smartlamp.dto.*;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.service.DeviceService;
import com.smartlamp.service.MqttPublisherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    @Autowired
    private DeviceCommandRepository deviceCommandRepository;

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
        if (deviceService.getDeviceByCode(deviceId) == null) {
            return ApiResponse.error(400, "设备不存在");
        }
        return dispatchCommand(deviceId, request.isOn() ? "ON" : "OFF");
    }

    // POST /api/devices/{deviceId}/control —— 兼容旧契约
    @PostMapping("/{deviceId}/control")
    public ApiResponse<String> control(@PathVariable String deviceId,
                                       @RequestBody DeviceControlRequest request) {
        if (deviceService.getDeviceByCode(deviceId) == null) {
            return ApiResponse.error(400, "设备不存在");
        }
        String action = request.getAction();
        if (action == null || !action.matches("ON|OFF")) {
            return ApiResponse.error(400, "action 必须为 ON 或 OFF");
        }
        return dispatchCommand(deviceId, action);
    }

    // 私有方法：生成指令并发布
    private ApiResponse<String> dispatchCommand(String deviceId, String action) {
        String commandId = UUID.randomUUID().toString();

        // 1. 保存指令记录，状态为 DISPATCHED
        DeviceCommand command = new DeviceCommand();
        command.setCommandId(commandId);
        command.setDeviceCode(deviceId);
        command.setAction(action);
        command.setStatus(CommandStatus.DISPATCHED);
        command.setCreatedAt(LocalDateTime.now());
        command.setUpdatedAt(LocalDateTime.now());
        deviceCommandRepository.save(command);

        // 2. 发布 MQTT 指令，携带 commandId
        boolean on = "ON".equals(action);
        String payload = "{\"deviceId\":\"" + deviceId + "\",\"on\":" + on + ",\"commandId\":\"" + commandId + "\"}";
        String topic = "device/" + deviceId + "/cmd";
        mqttPublisherService.publish(topic, payload);

        // 3. 返回 commandId 给前端，前端可通过查询接口获取最终状态
        return ApiResponse.success(commandId);
    }

    // GET /api/devices/commands/{commandId} 查询指令状态
    @GetMapping("/commands/{commandId}")
    public ApiResponse<DeviceCommand> getCommandStatus(@PathVariable String commandId) {
        DeviceCommand command = deviceCommandRepository.findByCommandId(commandId).orElse(null);
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