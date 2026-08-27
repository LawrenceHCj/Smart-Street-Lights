package com.smartlamp.agent.actions;

import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.service.ConfigService;
import com.smartlamp.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Action 确认/取消服务（阶段17/20）：用户确认接口的唯一业务入口。
// 确认时执行二次校验，全部通过才允许执行；校验不通过该 Action 置 FAILED（终态）并拒绝：
//   Action 存在 → 未过期 → 状态仍为 PENDING_CONFIRMATION → 参数合法复核
//   → 设备类目标：设备仍存在 → 仍在线 → 未处于目标状态
//   → 配置类目标（阶段20）：当前配置未在确认前被改成目标值（状态已变化则拒绝）
// 全部通过后：PENDING_CONFIRMATION → CONFIRMED →（ActionGateway）EXECUTING → SUCCESS / COMMAND_ACCEPTED / FAILED。
// 真正执行对象一律通过 actionId 找到，绝不重新让大模型猜测"确认的是什么"。
@Service
public class ActionService {

    @Autowired
    private ActionManager actionManager;

    @Autowired
    private ActionGateway actionGateway;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private ConfigService configService;

    // 用户确认：二次校验通过 → 确认 → 立即交由网关执行
    public synchronized AgentAction confirmAndExecute(String actionId) {
        AgentAction action = requirePending(actionId);

        // 参数合法性复核（创建时已校验，此处为二次防线）
        actionManager.revalidate(action);

        // 按目标类型执行确认前二次校验（只读）
        if ("device".equals(action.getTargetType())) {
            checkDeviceForConfirm(action);
        } else if ("config".equals(action.getTargetType())) {
            checkConfigForConfirm(action);
        }

        // 二次校验全部通过：确认后立即执行（PENDING → CONFIRMED → EXECUTING → SUCCESS / COMMAND_ACCEPTED / FAILED）
        actionManager.confirm(actionId);
        return actionGateway.execute(actionId);
    }

    // 设备类目标二次校验（只读）：确认时设备可能已被删除 / 离线 / 状态已变化
    private void checkDeviceForConfirm(AgentAction action) {
        Device device = deviceService.getDeviceByCode(action.getTargetId());
        if (device == null) {
            reject(action, "确认时校验未通过：设备不存在（可能已被删除）: " + action.getTargetId());
        }
        if (!"ONLINE".equals(device.getStatus())) {
            reject(action, "确认时校验未通过：设备当前离线: " + action.getTargetId()
                    + "（当前状态: " + device.getStatus() + "）");
        }
        String lampStatus = device.getLampStatus() == null ? "UNKNOWN" : device.getLampStatus();
        if (isGoalState(action.getActionType(), lampStatus)) {
            reject(action, "确认时校验未通过：设备状态已变化，已处于目标状态（" + lampStatus + "），无需执行");
        }
    }

    // 配置类目标二次校验（只读，阶段20）：确认前配置已被改成目标值 → 拒绝（无需执行）
    private void checkConfigForConfirm(AgentAction action) {
        LinkageConfigDTO current = configService.getLinkageConfig();
        boolean alreadyApplied = switch (action.getActionType()) {
            case UPDATE_LUX_THRESHOLD ->
                    current.getThreshold() == ((Number) action.getArguments().get("value")).intValue();
            case UPDATE_AUTO_MODE ->
                    current.isEnabled() == (Boolean) action.getArguments().get("enabled");
            default -> false;
        };
        if (alreadyApplied) {
            reject(action, "确认时校验未通过：配置状态已变化，当前已是目标值，无需执行");
        }
    }

    // 用户取消：仅允许取消 PENDING_CONFIRMATION 状态的 Action
    public synchronized AgentAction cancel(String actionId) {
        AgentAction action = requirePending(actionId);
        actionManager.cancel(actionId);
        return action;
    }

    // 取消指定会话的全部待确认 Action（阶段30：会话删除时的安全处理，由 AgentConversationService 调用）。
    // conversationId 仅用于溯源关联，确认/取消接口仍只认 actionId。
    public int cancelPendingByConversation(String conversationId) {
        return actionManager.cancelPendingByConversation(conversationId);
    }

    // 存在性 → 有效期 → 状态仍为 PENDING_CONFIRMATION
    private AgentAction requirePending(String actionId) {
        AgentAction action = actionManager.find(actionId)
                .orElseThrow(() -> new ActionRejectedException("Action 不存在: " + actionId));
        actionManager.checkExpiry(action);
        if (action.getStatus() != ActionStatus.PENDING_CONFIRMATION) {
            throw new ActionRejectedException("只有待确认状态的 Action 才能操作（当前: "
                    + action.getStatus() + "），请勿重复操作");
        }
        return action;
    }

    // 二次校验不通过：Action 置 FAILED（终态）并拒绝，绝不进入执行
    private void reject(AgentAction action, String reason) {
        actionManager.markFailure(action.getActionId(), reason);
        throw new ActionRejectedException(reason + "，未执行任何控制");
    }

    // 目标状态判断：开灯请求要求当前不是 ON，关灯请求要求当前不是 OFF
    private boolean isGoalState(ActionType type, String lampStatus) {
        return (type == ActionType.TURN_ON_LIGHT && "ON".equals(lampStatus))
                || (type == ActionType.TURN_OFF_LIGHT && "OFF".equals(lampStatus));
    }
}
