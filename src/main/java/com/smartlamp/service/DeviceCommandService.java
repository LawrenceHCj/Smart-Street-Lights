package com.smartlamp.service;

import com.smartlamp.dto.ControlResultDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.exception.BadRequestException;
import com.smartlamp.repository.DeviceCommandRepository;
import com.smartlamp.repository.DeviceRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
public class DeviceCommandService {
    private static final long COMMAND_TIMEOUT_SECONDS = 60;

    private final DeviceCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;
    private final MqttPublisherService mqttPublisherService;

    public DeviceCommandService(DeviceCommandRepository commandRepository,
                                DeviceRepository deviceRepository,
                                MqttPublisherService mqttPublisherService) {
        this.commandRepository = commandRepository;
        this.deviceRepository = deviceRepository;
        this.mqttPublisherService = mqttPublisherService;
    }

    @Transactional
    public DeviceCommand dispatch(String deviceId, String action, String mode) {
        Device device = deviceRepository.findByCode(deviceId)
                .orElseThrow(() -> new BadRequestException("设备不存在: " + deviceId));

        // 检查设备是否绑定
        if (!Boolean.TRUE.equals(device.getBound())) {
            throw new BadRequestException("设备未绑定: " + deviceId);
        }

        // ========== 新增：检查设备是否在线 ==========
        if (device.getStatus() == null || !"ONLINE".equalsIgnoreCase(device.getStatus())) {
            throw new BadRequestException("设备离线，无法下发指令");
        }
        // ===========================================

        String normalizedAction = normalizeAction(action);
        LocalDateTime now = LocalDateTime.now();
        DeviceCommand command = new DeviceCommand();
        command.setCommandId(UUID.randomUUID().toString());
        command.setDeviceCode(deviceId);
        command.setAction(normalizedAction);
        command.setStatus(CommandStatus.DISPATCHED);
        command.setCreatedAt(now);
        command.setUpdatedAt(now);
        commandRepository.save(command);

        boolean on = "ON".equals(normalizedAction);
        String payload = "{\"deviceId\":\"" + deviceId + "\",\"action\":\"" + normalizedAction
                + "\",\"on\":" + on + ",\"commandId\":\"" + command.getCommandId()
                + "\",\"mode\":\"" + safeMode(mode) + "\"}";
        try {
            mqttPublisherService.publish("device/" + deviceId + "/cmd", payload);
        } catch (RuntimeException error) {
            command.setStatus(CommandStatus.FAILED);
            command.setUpdatedAt(LocalDateTime.now());
            commandRepository.save(command);
            throw error;
        }
        return command;
    }

    public DeviceCommand find(String commandId) {
        return commandRepository.findByCommandId(commandId)
                .orElseThrow(() -> new BadRequestException("指令不存在: " + commandId));
    }

    @Transactional
    public void acknowledge(String topicDeviceId, JsonNode payload) {
        String commandId = requiredText(payload, "commandId");
        String statusText = requiredText(payload, "status").toUpperCase(Locale.ROOT);
        CommandStatus status;
        try {
            status = CommandStatus.valueOf(statusText);
        } catch (IllegalArgumentException error) {
            throw new BadRequestException("不支持的指令回执状态: " + statusText);
        }
        if (status != CommandStatus.ACKED && status != CommandStatus.SUCCESS && status != CommandStatus.FAILED) {
            throw new BadRequestException("设备只允许回执 ACKED、SUCCESS 或 FAILED");
        }

        DeviceCommand command = find(commandId);
        if (!topicDeviceId.equals(command.getDeviceCode())) {
            throw new BadRequestException("回执设备与指令目标不一致");
        }
        command.setStatus(status);
        command.setUpdatedAt(LocalDateTime.now());
        commandRepository.save(command);

        if (status == CommandStatus.SUCCESS) {
            deviceRepository.findByCode(topicDeviceId).ifPresent(device -> {
                device.setLampStatus(command.getAction());
                deviceRepository.save(device);
            });
        }
    }

    public ControlResultDTO toResult(DeviceCommand command, String mode) {
        long issuedAt = command.getCreatedAt().toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        return new ControlResultDTO(command.getCommandId(), command.getDeviceCode(), command.getAction(),
                safeMode(mode), command.getStatus().name(), issuedAt, commandMessage(command.getStatus()));
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void markTimedOutCommands() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(COMMAND_TIMEOUT_SECONDS);
        for (DeviceCommand command : commandRepository.findByStatusAndCreatedAtBefore(CommandStatus.DISPATCHED, cutoff)) {
            command.setStatus(CommandStatus.TIMEOUT);
            command.setUpdatedAt(LocalDateTime.now());
            commandRepository.save(command);
        }
        for (DeviceCommand command : commandRepository.findByStatusAndCreatedAtBefore(CommandStatus.ACKED, cutoff)) {
            command.setStatus(CommandStatus.TIMEOUT);
            command.setUpdatedAt(LocalDateTime.now());
            commandRepository.save(command);
        }
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!"ON".equals(normalized) && !"OFF".equals(normalized)) {
            throw new BadRequestException("action 必须为 ON 或 OFF");
        }
        return normalized;
    }

    private String safeMode(String mode) {
        return mode == null || mode.isBlank() ? "MANUAL" : mode.trim().toUpperCase(Locale.ROOT);
    }

    private String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new BadRequestException(field + " 必须是非空字符串");
        }
        return value.asText().trim();
    }

    private String commandMessage(CommandStatus status) {
        return switch (status) {
            case DISPATCHED -> "控制命令已发送，等待设备回执";
            case ACKED -> "设备已接收命令，等待执行结果";
            case SUCCESS -> "设备执行成功";
            case FAILED -> "设备执行失败";
            case TIMEOUT -> "等待设备回执超时";
        };
    }
}