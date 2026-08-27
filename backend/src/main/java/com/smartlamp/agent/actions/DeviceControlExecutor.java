package com.smartlamp.agent.actions;

import com.smartlamp.dto.CommandStatus;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.service.DeviceCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 真实控制执行器（阶段18 + 整合修复#5）：Agent Write Tool → Action Gateway → 3号 DeviceCommandService（命令表）→ MQTT → 设备。
// 与网页控制统一走 DeviceCommandService（dispatch 生成 DeviceCommand 记录并下发命令），
// 阶段18 代写的 DeviceControlService 独立链路已删除。
// 结果按命令表真实状态如实报告：
//  - 等待窗口内收到 SUCCESS 回执 → DEVICE_CONFIRMED（真正成功）
//  - FAILED/TIMEOUT → 对应失败结果
//  - 窗口结束仍未获回执 → COMMAND_ACCEPTED（命令在途，3号 超时任务会在 60 秒后置 TIMEOUT）
@Component
public class DeviceControlExecutor implements ActionExecutor {

    private static final long POLL_INTERVAL_MS = 200;

    private final DeviceCommandService deviceCommandService;

    // 等待设备回执的窗口（毫秒，可配置；置 0 表示不等待，直接返回 COMMAND_ACCEPTED）
    private final long ackTimeoutMs;

    // 构造时注册到 ActionGateway（Bean 创建即生效）
    public DeviceControlExecutor(ActionGateway actionGateway,
                                 DeviceCommandService deviceCommandService,
                                 @Value("${agent.ack-timeout-ms:5000}") long ackTimeoutMs) {
        this.deviceCommandService = deviceCommandService;
        this.ackTimeoutMs = ackTimeoutMs;
        actionGateway.registerExecutor(ActionType.TURN_ON_LIGHT, this);
        actionGateway.registerExecutor(ActionType.TURN_OFF_LIGHT, this);
    }

    @Override
    public ExecutorResult execute(AgentAction action) {
        String target = action.getActionType() == ActionType.TURN_ON_LIGHT ? "ON" : "OFF";

        // 与网页控制同一链路：命令表落库 + MQTT 下发（存在/绑定校验由 DeviceCommandService 承担）
        DeviceCommand command;
        try {
            command = deviceCommandService.dispatch(action.getTargetId(), target, "AGENT");
        } catch (Exception e) {
            return new ExecutorResult(CommandStatus.FAILED, "FAILED：命令下发失败: " + e.getMessage());
        }
        return waitForAck(command);
    }

    // 按命令表状态轮询设备回执（3号 的 cmd_ack 链路收到回执后更新状态）
    private ExecutorResult waitForAck(DeviceCommand command) {
        long deadline = System.currentTimeMillis() + ackTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            switch (command.getStatus()) {
                case SUCCESS -> {
                    return new ExecutorResult(CommandStatus.DEVICE_CONFIRMED,
                            "DEVICE_CONFIRMED：设备已确认执行（命令号 " + command.getCommandId() + "）");
                }
                case FAILED -> {
                    return new ExecutorResult(CommandStatus.FAILED,
                            "FAILED：设备执行失败（命令号 " + command.getCommandId() + "）");
                }
                case TIMEOUT -> {
                    return new ExecutorResult(CommandStatus.TIMEOUT,
                            "TIMEOUT：等待设备回执超时（命令号 " + command.getCommandId() + "）");
                }
                default -> {
                    // DISPATCHED / ACKED：继续等待回执
                }
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            command = deviceCommandService.find(command.getCommandId());
        }
        // 等待窗口结束仍未获终态：命令在途，如实报告"已下发、未获回执"
        return new ExecutorResult(CommandStatus.COMMAND_ACCEPTED,
                "COMMAND_ACCEPTED：控制指令已发送，但当前尚未获得设备执行确认（命令号 " + command.getCommandId() + "）");
    }
}
