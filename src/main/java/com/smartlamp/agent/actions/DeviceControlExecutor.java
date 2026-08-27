package com.smartlamp.agent.actions;

import com.smartlamp.dto.CommandStatus;
import com.smartlamp.dto.ControlOutcome;
import com.smartlamp.service.DeviceControlService;
import org.springframework.stereotype.Component;

// 真实控制执行器（阶段18）：Agent Write Tool → Action Gateway → 3号 DeviceControlService → MQTT → 设备。
// 本类只做白名单 Action 到正式 Service 的映射与结果如实报告：
//  - COMMAND_ACCEPTED / DEVICE_CONFIRMED → 返回 ExecutorResult，由 ActionGateway 置相应终态
//  - FAILED / TIMEOUT → 返回 ExecutorResult，由 ActionGateway 置 FAILED
// 不直接 publish MQTT、不修改数据库、不实现任何灯控逻辑（全部由 DeviceControlService 承担）。
@Component
public class DeviceControlExecutor implements ActionExecutor {

    private final DeviceControlService deviceControlService;

    // 构造时注册到 ActionGateway（Bean 创建即生效），替换阶段17 的 MockDeviceExecutor
    public DeviceControlExecutor(ActionGateway actionGateway, DeviceControlService deviceControlService) {
        this.deviceControlService = deviceControlService;
        actionGateway.registerExecutor(ActionType.TURN_ON_LIGHT, this);
        actionGateway.registerExecutor(ActionType.TURN_OFF_LIGHT, this);
    }

    @Override
    public ExecutorResult execute(AgentAction action) {
        ControlOutcome outcome = action.getActionType() == ActionType.TURN_ON_LIGHT
                ? deviceControlService.turnOnLight(action.getTargetId())
                : deviceControlService.turnOffLight(action.getTargetId());

        return switch (outcome.getStatus()) {
            case COMMAND_ACCEPTED -> new ExecutorResult(CommandStatus.COMMAND_ACCEPTED,
                    "COMMAND_ACCEPTED：控制指令已发送，但当前尚未获得设备执行确认（命令号 " + outcome.getCommandId() + "）");
            case DEVICE_CONFIRMED -> new ExecutorResult(CommandStatus.DEVICE_CONFIRMED,
                    "DEVICE_CONFIRMED：设备已确认执行（命令号 " + outcome.getCommandId() + "）");
            case FAILED -> new ExecutorResult(CommandStatus.FAILED, "FAILED：" + outcome.getMessage());
            case TIMEOUT -> new ExecutorResult(CommandStatus.TIMEOUT, "TIMEOUT：" + outcome.getMessage());
        };
    }
}
