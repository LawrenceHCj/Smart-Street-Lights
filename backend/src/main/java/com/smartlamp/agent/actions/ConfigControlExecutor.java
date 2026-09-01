package com.smartlamp.agent.actions;

import com.smartlamp.dto.CommandStatus;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.service.ConfigService;
import org.springframework.stereotype.Component;

// 系统配置控制执行器（阶段20）：Agent Write Tool → Action Gateway → 3号 ConfigService（联动配置）。
// 只做白名单配置 Action 到正式 Service 的映射，保持其余配置字段不变；
// 不直接修改配置表、不直接发布 MQTT（下发由 ConfigService 内部完成）。
// 配置保存成功只代表"已保存并下发"，设备无执行确认回执，如实返回 COMMAND_ACCEPTED。
@Component
public class ConfigControlExecutor implements ActionExecutor {

    private final ConfigService configService;

    // 构造时注册到 ActionGateway（Bean 创建即生效）
    public ConfigControlExecutor(ActionGateway actionGateway, ConfigService configService) {
        this.configService = configService;
        actionGateway.registerExecutor(ActionType.UPDATE_LUX_THRESHOLD, this);
        actionGateway.registerExecutor(ActionType.UPDATE_AUTO_MODE, this);
    }

    @Override
    public ExecutorResult execute(AgentAction action) {
        try {
            // 读取当前配置，仅修改目标字段，其余字段保持不变
            LinkageConfigDTO current = configService.getLinkageConfig();
            LinkageConfigDTO next = new LinkageConfigDTO();
            next.setEnabled(current.isEnabled());
            next.setThreshold(current.getThreshold());
            next.setHysteresis(current.getHysteresis());
            next.setBrightnessScheduleEnabled(current.getBrightnessScheduleEnabled());
            next.setBrightnessPeriods(current.getBrightnessPeriods());

            if (action.getActionType() == ActionType.UPDATE_LUX_THRESHOLD) {
                next.setThreshold(((Number) action.getArguments().get("value")).intValue());
            } else {
                next.setEnabled((Boolean) action.getArguments().get("enabled"));
            }
            configService.saveLinkageConfig(next);

            // 目标描述从 arguments 自行推导（不依赖工具层写入的 targetState 快照）
            String change = action.getActionType() == ActionType.UPDATE_LUX_THRESHOLD
                    ? "threshold=" + action.getArguments().get("value")
                    : "autoControl=" + action.getArguments().get("enabled");
            return new ExecutorResult(CommandStatus.COMMAND_ACCEPTED,
                    "COMMAND_ACCEPTED：配置已保存并下发到已绑定设备（" + change
                            + "），设备将按新配置执行自动控制，当前无设备执行确认回执");
        } catch (Exception e) {
            return new ExecutorResult(CommandStatus.FAILED, "FAILED：配置保存失败: " + e.getMessage());
        }
    }
}
