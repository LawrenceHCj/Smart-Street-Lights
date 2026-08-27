package com.smartlamp.agent.tools;

import com.smartlamp.agent.AgentCallContext;
import com.smartlamp.agent.actions.ActionGateway;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 控制意图工具（Agent V2）：
//  - 开灯/关灯：**免二次确认自动执行**（权限调整后与查询同级）——先按角色权限校验
//    （与网页控制同一规则 admin/operator，见 SecurityConfig @PreAuthorize），
//    再做存在/在线/目标状态检查，全部通过后经 ActionGateway 立即执行并如实返回结果；
//    设备不存在或离线、当前已是目标状态、角色无权限时拒绝且不创建执行。
//  - 修改阈值/自动模式：仍需用户二次确认（生成 PENDING Action + 确认接口）。
// 安全兜底：执行永远经 ActionGateway（状态机 + 白名单），Agent 不直连 MQTT/数据库。
@Component
public class AgentActionTools {

    // 控制权限角色：与后端接口 @PreAuthorize("hasAnyRole('admin','operator')") 同一规则
    private static final Set<String> CONTROL_ROLES = Set.of("admin", "operator");

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private ActionManager actionManager;

    @Autowired
    private ActionGateway actionGateway;

    @Autowired
    private AgentActionAuditService agentActionAuditService;

    @Autowired
    private ConfigService configService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 开灯请求：权限校验 → 设备检查 → 自动执行（免确认），如实返回执行结果
    public ObjectNode requestTurnOn(JsonNode args) {
        return requestDeviceControl(ActionType.TURN_ON_LIGHT, args);
    }

    // 关灯请求：权限校验 → 设备检查 → 自动执行（免确认），如实返回执行结果
    public ObjectNode requestTurnOff(JsonNode args) {
        return requestDeviceControl(ActionType.TURN_OFF_LIGHT, args);
    }

    // 阈值修改请求（阶段20）：value 必须为明确数值，合法范围由后端业务规则（10-500）校验；
    // 仍需用户二次确认
    public ObjectNode requestSetThreshold(JsonNode args) {
        JsonNode value = args.path("value");
        if (!value.isNumber()) {
            throw new BadRequestException("value 必须是明确数值（10-500）；用户说法模糊时请先查询当前配置并给出候选值让用户确认");
        }
        return requestConfigAction(ActionType.UPDATE_LUX_THRESHOLD, Map.of("value", value.intValue()));
    }

    // 自动模式修改请求（阶段20）：enabled 为布尔；仍需用户二次确认
    public ObjectNode requestSetAutoMode(JsonNode args) {
        JsonNode enabled = args.path("enabled");
        if (!enabled.isBoolean()) {
            throw new BadRequestException("enabled 必须是布尔值（true 开启自动控制，false 关闭）");
        }
        return requestConfigAction(ActionType.UPDATE_AUTO_MODE, Map.of("enabled", enabled.booleanValue()));
    }

    // 批量关闭请求（权限调整后开放）：仍需二次确认——生成 PENDING Action 返回 actionId，
    // 用户在前端聊天确认卡片点击确认后经确认接口执行（绝不自动执行）
    public ObjectNode requestTurnOffAll(JsonNode args) {
        ObjectNode node = actionNode(ActionType.TURN_OFF_ALL);

        // 角色权限校验（与网页控制同一规则）
        String role = currentRole();
        if (role == null || !CONTROL_ROLES.contains(role)) {
            node.put("status", "REJECTED_NO_PERMISSION");
            node.put("message", "当前角色无控制权限（仅 admin/operator 可控制设备），已拒绝");
            return node;
        }

        // 只读快照：当前在线且已绑定的设备
        List<Device> targets = new ArrayList<>();
        for (Device device : deviceService.getAllDevices()) {
            if (Boolean.TRUE.equals(device.getBound()) && "ONLINE".equals(device.getStatus())) {
                targets.add(device);
            }
        }
        if (targets.isEmpty()) {
            node.put("status", "REJECTED_NO_TARGETS");
            node.put("message", "当前没有在线且已绑定的设备，无需执行批量关闭");
            return node;
        }
        String originalState = "在线设备 " + targets.size() + " 台: "
                + targets.stream().map(Device::getCode).collect(java.util.stream.Collectors.joining(","));

        // 生成待确认 Action（批量关闭必须用户确认，绝不由模型自动执行）
        AgentAction action = actionManager.create(ActionType.TURN_OFF_ALL, "device", "all", Map.of(), currentUser());
        action.setConversationId(AgentCallContext.getConversationId());
        action.setOriginalState(originalState);
        action.setTargetState("全部关闭（" + targets.size() + " 台）");
        agentActionAuditService.recordCreated(action);

        node.put("status", ActionStatus.PENDING_CONFIRMATION.name());
        node.put("actionId", action.getActionId());
        node.put("targetId", "all");
        node.put("riskLevel", action.getRiskLevel().name());
        node.put("expiresAt", action.getExpiresAt());
        node.put("summary", "关闭全部设备（" + targets.size() + " 台在线）");
        node.put("originalState", originalState);
        node.put("targetState", action.getTargetState());
        node.put("message", "已生成批量关闭请求（" + originalState
                + "）。请向用户展示确认卡片并等待确认，未确认绝不执行。");
        return node;
    }

    // 设备控制请求（开灯/关灯，免确认自动执行）：
    // 角色权限 → 存在 → 在线 → 未处于目标状态 → 创建 Action → 立即经网关执行 → 如实返回结果
    private ObjectNode requestDeviceControl(ActionType type, JsonNode args) {
        String code = requireText(args.path("deviceCode").asText(null), "deviceCode");
        ObjectNode node = actionNode(type);
        node.put("deviceCode", code);

        // 0. 角色权限校验（执行前判定，与网页控制同一规则）
        String role = currentRole();
        if (role == null || !CONTROL_ROLES.contains(role)) {
            node.put("status", "REJECTED_NO_PERMISSION");
            node.put("message", "当前角色无控制权限（仅 admin/operator 可控制设备），已拒绝且未执行任何操作");
            return node;
        }

        // 1. 设备存在性检查（只读）
        Device device = deviceService.getDeviceByCode(code);
        if (device == null) {
            node.put("status", "REJECTED_DEVICE_NOT_FOUND");
            node.put("message", "设备不存在: " + code + "，未执行任何操作");
            return node;
        }

        // 2. 设备在线检查（只读）：离线默认不继续控制
        if (!"ONLINE".equals(device.getStatus())) {
            node.put("status", "REJECTED_DEVICE_OFFLINE");
            node.put("deviceStatus", device.getStatus());
            node.put("message", "设备当前离线: " + code + "（当前状态: " + device.getStatus() + "），默认不继续控制，请先排查离线原因");
            return node;
        }

        // 3. 当前开关状态（只读，如实报告）
        String lampStatus = device.getLampStatus() == null ? "UNKNOWN" : device.getLampStatus();
        String target = type == ActionType.TURN_ON_LIGHT ? "ON" : "OFF";
        node.put("deviceStatus", device.getStatus());
        node.put("lampStatus", lampStatus);
        if (target.equals(lampStatus)) {
            node.put("status", "REJECTED_NO_CHANGE");
            node.put("message", "设备已处于目标状态（" + lampStatus + "），无需执行");
            return node;
        }

        // 4. 创建 Action（发起者取自认证上下文）→ 立即确认并经网关执行（免二次确认，权限已在上方校验）
        AgentAction action = actionManager.create(type, "device", code, Map.of(), currentUser());
        action.setConversationId(AgentCallContext.getConversationId());
        action.setOriginalState(lampStatus);
        action.setTargetState(target);
        agentActionAuditService.recordCreated(action);

        node.put("actionId", action.getActionId());
        node.put("riskLevel", action.getRiskLevel().name());
        try {
            actionManager.confirm(action.getActionId());
            AgentAction executed = actionGateway.execute(action.getActionId());
            node.put("status", executed.getStatus().name());
            node.put("message", "已执行：" + executed.getMessage()
                    + "（设备: " + code + "，执行前状态: " + lampStatus + "）");
        } catch (ActionRejectedException e) {
            node.put("status", ActionStatus.FAILED.name());
            node.put("message", e.getMessage() + "（未产生实际控制效果）");
        }
        return node;
    }

    // 配置类请求：角色校验 → 读取当前配置 → 已是目标值则拒绝 → 生成 config 类待确认 Action（快照 + 创建审计）
    private ObjectNode requestConfigAction(ActionType type, Map<String, Object> arguments) {
        ObjectNode node = actionNode(type);

        // 角色权限校验（与确认接口同规则：仅 admin/operator 可发起配置修改）
        String role = currentRole();
        if (role == null || !CONTROL_ROLES.contains(role)) {
            node.put("status", "REJECTED_NO_PERMISSION");
            node.put("message", "当前角色无控制权限（仅 admin/operator 可修改配置），已拒绝");
            return node;
        }

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
        node.put("targetId", "system");
        node.put("riskLevel", action.getRiskLevel().name());
        node.put("expiresAt", action.getExpiresAt());
        node.put("summary", type.getDisplayName() + "：" + action.getTargetState());
        node.put("originalState", originalState);
        node.put("targetState", action.getTargetState());
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

    // 当前角色（安全上下文 authority 形如 ROLE_admin / ROLE_operator / ROLE_municipal）
    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return null;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(name + " 不能为空");
        }
        return value.trim();
    }
}
