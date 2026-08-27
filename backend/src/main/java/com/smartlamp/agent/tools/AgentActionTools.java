package com.smartlamp.agent.tools;

import com.smartlamp.agent.AgentCallContext;
import com.smartlamp.agent.actions.ActionManager;
import com.smartlamp.agent.actions.ActionRejectedException;
import com.smartlamp.agent.actions.ActionStatus;
import com.smartlamp.agent.actions.ActionType;
import com.smartlamp.agent.actions.AgentAction;
import com.smartlamp.agent.actions.AgentActionAuditService;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.exception.BadRequestException;
import com.smartlamp.service.ConfigService;
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

    @Autowired
    private AgentActionAuditService agentActionAuditService;

    @Autowired
    private ConfigService configService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 开灯请求：检查通过后生成待确认 Action（LOW_WRITE → PENDING_CONFIRMATION）
    public ObjectNode requestTurnOn(JsonNode args) {
        return request(ActionType.TURN_ON_LIGHT, args);
    }

    // 关灯请求：检查通过后生成待确认 Action（LOW_WRITE → PENDING_CONFIRMATION）
    public ObjectNode requestTurnOff(JsonNode args) {
        return request(ActionType.TURN_OFF_LIGHT, args);
    }

    // 阈值修改请求（阶段20）：value 必须为明确数值，合法范围由后端业务规则（10-500）校验，
    // 模糊说法（"调高一点"）由大模型先查询当前配置并给候选值，本工具不接受模糊值
    public ObjectNode requestSetThreshold(JsonNode args) {
        JsonNode value = args.path("value");
        if (!value.isNumber()) {
            throw new BadRequestException("value 必须是明确数值（10-500）；用户说法模糊时请先查询当前配置并给出候选值让用户确认");
        }
        return requestConfigAction(ActionType.UPDATE_LUX_THRESHOLD, Map.of("value", value.intValue()));
    }

    // 自动模式修改请求（阶段20）：enabled 为布尔
    public ObjectNode requestSetAutoMode(JsonNode args) {
        JsonNode enabled = args.path("enabled");
        if (!enabled.isBoolean()) {
            throw new BadRequestException("enabled 必须是布尔值（true 开启自动控制，false 关闭）");
        }
        return requestConfigAction(ActionType.UPDATE_AUTO_MODE, Map.of("enabled", enabled.booleanValue()));
    }

    // 配置类请求：读取当前配置 → 已是目标值则拒绝 → 生成 config 类待确认 Action（快照 + 创建审计）
    private ObjectNode requestConfigAction(ActionType type, Map<String, Object> arguments) {
        ObjectNode node = actionNode(type);
        LinkageConfigDTO current = configService.getLinkageConfig();
        String originalState = "{\"auto\":" + current.isEnabled()
                + ",\"threshold\":" + current.getThreshold()
                + ",\"hysteresis\":" + current.getHysteresis() + "}";

        // 目标已是当前值 → 无需修改
        if (isNoChange(type, arguments, current)) {
            node.put("status", "REJECTED_NO_CHANGE");
            node.put("message", "当前配置已是该值，无需修改（当前配置: " + originalState + "）");
            return node;
        }

        // 生成待确认 Action（参数合法性由 ActionManager 白名单校验，失败时返回结构化拒绝结果）
        AgentAction action;
        try {
            action = actionManager.create(type, "config", "system", arguments, currentUser());
        } catch (ActionRejectedException e) {
            node.put("status", "REJECTED_INVALID_VALUE");
            node.put("message", e.getMessage() + "；请如实告知用户合法范围，不得自行决定合法数值");
            return node;
        }
        action.setConversationId(AgentCallContext.getConversationId());
        action.setOriginalState(originalState);
        action.setTargetState(targetDescription(type, arguments));
        agentActionAuditService.recordCreated(action);

        node.put("status", ActionStatus.PENDING_CONFIRMATION.name());
        node.put("actionId", action.getActionId());
        node.put("riskLevel", action.getRiskLevel().name());
        node.put("currentConfig", originalState);
        node.put("message", "已生成待确认配置修改请求：" + type.getDisplayName()
                + "（目标: " + action.getTargetState() + "，当前配置: " + originalState
                + "）。请向用户转达确认请求并等待确认，未确认绝不执行。");
        return node;
    }

    // 目标已是当前配置值（无需修改）
    private boolean isNoChange(ActionType type, Map<String, Object> arguments, LinkageConfigDTO current) {
        if (type == ActionType.UPDATE_LUX_THRESHOLD) {
            return current.getThreshold() == ((Number) arguments.get("value")).intValue();
        }
        if (type == ActionType.UPDATE_AUTO_MODE) {
            return current.isEnabled() == (Boolean) arguments.get("enabled");
        }
        return false;
    }

    // 审计用的目标状态描述（如 threshold=150 / autoControl=false）
    private String targetDescription(ActionType type, Map<String, Object> arguments) {
        if (type == ActionType.UPDATE_LUX_THRESHOLD) {
            return "threshold=" + arguments.get("value");
        }
        if (type == ActionType.UPDATE_AUTO_MODE) {
            return "autoControl=" + arguments.get("enabled");
        }
        return type.name();
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
        // 来源会话溯源（阶段30，仅关联记录；确认永远只认 actionId）
        action.setConversationId(AgentCallContext.getConversationId());
        // 状态快照供审计：originalState=创建时 lampStatus，targetState=目标开关状态
        action.setOriginalState(lampStatus);
        action.setTargetState(type == ActionType.TURN_ON_LIGHT ? "ON" : "OFF");
        agentActionAuditService.recordCreated(action);
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
