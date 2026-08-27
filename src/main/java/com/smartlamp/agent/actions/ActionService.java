package com.smartlamp.agent.actions;

import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.service.ConfigService;
import com.smartlamp.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public synchronized AgentAction confirmAndExecute(String actionId, String username) {
        // 新增：归属校验，必须由发起人确认
        AgentAction action = actionManager.find(actionId).orElse(null);
        if (action == null || !username.equals(action.getRequestedBy())) {
            return null;   // 无权操作
        }

        action = requirePending(actionId);

        // 参数合法性复核
        actionManager.revalidate(action);

        if ("device".equals(action.getTargetType())) {
            checkDeviceForConfirm(action);
        } else if ("config".equals(action.getTargetType())) {
            checkConfigForConfirm(action);
        }

        actionManager.confirm(actionId);
        return actionGateway.execute(actionId);
    }

    // 用户取消：仅允许取消 PENDING_CONFIRMATION 状态的 Action，且必须为发起人
    public synchronized AgentAction cancel(String actionId, String username) {
        AgentAction action = actionManager.find(actionId).orElse(null);
        if (action == null || !username.equals(action.getRequestedBy())) {
            return null;
        }

        action = requirePending(actionId);
        actionManager.cancel(actionId);
        return action;
    }

    public int cancelPendingByConversation(String conversationId) {
        return actionManager.cancelPendingByConversation(conversationId);
    }

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

    private void reject(AgentAction action, String reason) {
        actionManager.markFailure(action.getActionId(), reason);
        throw new ActionRejectedException(reason + "，未执行任何控制");
    }

    private boolean isGoalState(ActionType type, String lampStatus) {
        return (type == ActionType.TURN_ON_LIGHT && "ON".equals(lampStatus))
                || (type == ActionType.TURN_OFF_LIGHT && "OFF".equals(lampStatus));
    }
}