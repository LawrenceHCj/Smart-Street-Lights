package com.smartlamp.agent.tools;

import com.smartlamp.agent.actions.ActionManager;
import com.smartlamp.agent.actions.ActionStatus;
import com.smartlamp.agent.actions.ActionType;
import com.smartlamp.agent.actions.AgentAction;
import com.smartlamp.entity.Device;
import com.smartlamp.exception.BadRequestException;
import com.smartlamp.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

// 控制意图工具（Agent V2 阶段16）：识别用户控制意图并生成"待确认 Action"，
// 本阶段绝不真正控制设备——不调用控制 Service、不发 MQTT、不改数据库状态。
// 生成 Action 前先做只读检查：设备是否存在 → 是否在线 → 当前开关状态；
// 设备不存在或离线时拒绝并说明原因，不创建任何 Action。
@Component
public class AgentActionTools {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private ActionManager actionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 开灯请求：检查通过后生成待确认 Action（LOW_WRITE → PENDING_CONFIRMATION）
    public ObjectNode requestTurnOn(JsonNode args) {
        return request(ActionType.TURN_ON_LIGHT, args);
    }

    // 关灯请求：检查通过后生成待确认 Action（LOW_WRITE → PENDING_CONFIRMATION）
    public ObjectNode requestTurnOff(JsonNode args) {
        return request(ActionType.TURN_OFF_LIGHT, args);
    }

    private ObjectNode request(ActionType type, JsonNode args) {
        String code = requireText(args.path("deviceCode").asText(null), "deviceCode");
        ObjectNode node = actionNode(type);
        node.put("deviceCode", code);

        // 1. 设备存在性检查（只读）
        Device device = deviceService.getDeviceByCode(code);
        if (device == null) {
            node.put("status", "REJECTED_DEVICE_NOT_FOUND");
            node.put("message", "设备不存在: " + code + "，未创建任何操作请求");
            return node;
        }

        // 2. 设备在线检查（只读）：离线默认不继续控制
        if (!"ONLINE".equals(device.getStatus())) {
            node.put("status", "REJECTED_DEVICE_OFFLINE");
            node.put("deviceStatus", device.getStatus());
            node.put("message", "设备当前离线: " + code + "（当前状态: " + device.getStatus() + "），默认不继续控制，请先排查离线原因");
            return node;
        }

        // 3. 当前开关状态（只读，如实报告给用户）
        String lampStatus = device.getLampStatus() == null ? "UNKNOWN" : device.getLampStatus();
        node.put("deviceStatus", device.getStatus());
        node.put("lampStatus", lampStatus);

        // 4. 生成待确认 Action（绝不执行；发起者取自认证上下文，不由大模型指定）
        AgentAction action = actionManager.create(type, "device", code, Map.of(), currentUser());
        node.put("status", ActionStatus.PENDING_CONFIRMATION.name());
        node.put("actionId", action.getActionId());
        node.put("riskLevel", action.getRiskLevel().name());
        node.put("message", "已生成待确认操作请求：" + type.getDisplayName()
                + "（设备: " + code + "，当前在线，当前开关状态: " + lampStatus
                + "）。请向用户转达确认请求并等待确认，未确认绝不执行。");
        return node;
    }

    private ObjectNode actionNode(ActionType type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("source", "action");
        node.put("actionType", type.name());
        return node;
    }

    // 发起者：从当前认证上下文取用户名（JwtAuthenticationFilter 已设置）；无认证时记 unknown
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "unknown";
        }
        return auth.getName();
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(name + " 不能为空");
        }
        return value.trim();
    }
}
