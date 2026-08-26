package com.smartlamp.agent.actions;

import com.smartlamp.entity.Device;
import com.smartlamp.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Action 确认/取消服务（阶段17）：用户确认接口的唯一业务入口。
// 确认时执行二次校验，全部通过才允许执行；校验不通过该 Action 置 FAILED（终态）并拒绝：
//   Action 存在 → 未过期 → 状态仍为 PENDING_CONFIRMATION → 参数合法复核
//   → 设备仍存在 → 设备仍在线 → 设备状态未变化（未处于目标状态）
// 全部通过后：PENDING_CONFIRMATION → CONFIRMED →（ActionGateway）EXECUTING → SUCCESS / FAILED。
// 真正执行对象一律通过 actionId 找到，绝不重新让大模型猜测"确认的是什么"。
// 本阶段执行器为 MockDeviceExecutor（明确标记 Mock，不真实控制设备、不发 MQTT、不改数据库）。
@Service
public class ActionService {

    @Autowired
    private ActionManager actionManager;

    @Autowired
    private ActionGateway actionGateway;

    @Autowired
    private DeviceService deviceService;

    // 用户确认：二次校验通过 → 确认 → 立即交由网关执行（本阶段为 Mock 执行器）
    public synchronized AgentAction confirmAndExecute(String actionId) {
        AgentAction action = requirePending(actionId);

        // 参数合法性复核（创建时已校验，此处为二次防线）
        actionManager.revalidate(action);

        // 设备二次校验（只读）：确认时设备可能已被删除 / 离线 / 状态已变化
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

        // 二次校验全部通过：确认后立即执行（PENDING → CONFIRMED → EXECUTING → SUCCESS / FAILED）
        actionManager.confirm(actionId);
        return actionGateway.execute(actionId);
    }

    // 用户取消：仅允许取消 PENDING_CONFIRMATION 状态的 Action
    public synchronized AgentAction cancel(String actionId) {
        AgentAction action = requirePending(actionId);
        actionManager.cancel(actionId);
        return action;
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
