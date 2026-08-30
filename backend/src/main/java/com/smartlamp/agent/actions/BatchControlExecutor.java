package com.smartlamp.agent.actions;

import com.smartlamp.dto.CommandStatus;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.service.DeviceCommandService;
import com.smartlamp.service.DeviceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 批量控制执行器（权限调整后开放"关闭全部设备"/"打开全部设备"，均需二次确认）：
// Agent Write Tool → Action Gateway → 3号 DeviceCommandService（逐台下发命令，与网页同一链路）。
// 执行时只对"已绑定且在线"的设备逐台下发（TURN_OFF_ALL→OFF，TURN_ON_ALL→ON），
// 并按命令表回执如实聚合报告：
//  - 全部确认执行 → DEVICE_CONFIRMED
//  - 任一失败 → FAILED（含失败数量）
//  - 窗口结束仍有未确认 → COMMAND_ACCEPTED（如实说明各台状态，绝不谎报全部成功）
@Component
public class BatchControlExecutor implements ActionExecutor {

    private static final long POLL_INTERVAL_MS = 200;

    private final DeviceService deviceService;
    private final DeviceCommandService deviceCommandService;
    private final long ackTimeoutMs;

    public BatchControlExecutor(ActionGateway actionGateway,
                                DeviceService deviceService,
                                DeviceCommandService deviceCommandService,
                                @Value("${agent.ack-timeout-ms:5000}") long ackTimeoutMs) {
        this.deviceService = deviceService;
        this.deviceCommandService = deviceCommandService;
        this.ackTimeoutMs = ackTimeoutMs;
        actionGateway.registerExecutor(ActionType.TURN_OFF_ALL, this);
        actionGateway.registerExecutor(ActionType.TURN_ON_ALL, this);
    }

    @Override
    public ExecutorResult execute(AgentAction action) {
        // 目标动作：TURN_ON_ALL → ON，TURN_OFF_ALL → OFF
        boolean turnOn = action.getActionType() == ActionType.TURN_ON_ALL;
        String targetCommand = turnOn ? "ON" : "OFF";
        String verb = turnOn ? "打开" : "关闭";

        // 1. 目标设备 = 已绑定且在线（只读检查；不满足的设备跳过，绝不向离线设备下发）
        List<Device> targets = new ArrayList<>();
        for (Device device : deviceService.getAllDevices()) {
            if (Boolean.TRUE.equals(device.getBound()) && "ONLINE".equals(device.getStatus())) {
                targets.add(device);
            }
        }
        if (targets.isEmpty()) {
            return new ExecutorResult(CommandStatus.FAILED, "FAILED：当前没有在线且已绑定的设备，未执行任何控制");
        }

        // 2. 逐台下发（与网页控制同一 DeviceCommandService）
        List<DeviceCommand> commands = new ArrayList<>();
        try {
            for (Device device : targets) {
                commands.add(deviceCommandService.dispatch(device.getCode(), targetCommand, "AGENT"));
            }
        } catch (Exception e) {
            return new ExecutorResult(CommandStatus.FAILED, "FAILED：批量" + verb + "下发中断: " + e.getMessage()
                    + "（已下发 " + commands.size() + "/" + targets.size() + " 台）");
        }

        // 3. 按命令表回执聚合（等待窗口内轮询）
        long deadline = System.currentTimeMillis() + ackTimeoutMs;
        int confirmed = 0;
        int failed = 0;
        int timedOut = 0;
        while (System.currentTimeMillis() < deadline) {
            confirmed = 0;
            failed = 0;
            timedOut = 0;
            for (DeviceCommand command : commands) {
                com.smartlamp.entity.enums.CommandStatus status = command.getStatus();
                if (status == com.smartlamp.entity.enums.CommandStatus.DISPATCHED
                        || status == com.smartlamp.entity.enums.CommandStatus.ACKED) {
                    // 重新读取最新状态
                    command.setStatus(deviceCommandService.find(command.getCommandId()).getStatus());
                    status = command.getStatus();
                }
                if (status == com.smartlamp.entity.enums.CommandStatus.SUCCESS) confirmed++;
                else if (status == com.smartlamp.entity.enums.CommandStatus.FAILED) failed++;
                else if (status == com.smartlamp.entity.enums.CommandStatus.TIMEOUT) timedOut++;
            }
            int terminal = confirmed + failed + timedOut;
            if (terminal == commands.size()) break; // 全部到达终态
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int unconfirmed = commands.size() - confirmed - failed - timedOut;
        String summary = "共下发 " + commands.size() + " 台" + verb + "命令："
                + confirmed + " 台已确认执行" + (failed > 0 ? "，" + failed + " 台执行失败" : "")
                + (timedOut > 0 ? "，" + timedOut + " 台回执超时" : "")
                + (unconfirmed > 0 ? "，" + unconfirmed + " 台尚未获得设备执行确认" : "");

        if (failed > 0) {
            return new ExecutorResult(CommandStatus.FAILED, "FAILED：批量" + verb + "未完全成功，" + summary);
        }
        if (confirmed == commands.size()) {
            return new ExecutorResult(CommandStatus.DEVICE_CONFIRMED, "DEVICE_CONFIRMED：批量" + verb + "完成，" + summary);
        }
        return new ExecutorResult(CommandStatus.COMMAND_ACCEPTED,
                "COMMAND_ACCEPTED：批量" + verb + "命令已下发，" + summary + "，请以设备状态为准");
    }
}
