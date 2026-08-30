package com.smartlamp.agent.tools;

import com.smartlamp.agent.AgentCallContext;
import com.smartlamp.agent.actions.ActionGateway;
import com.smartlamp.agent.actions.ActionManager;
import com.smartlamp.agent.actions.ActionRejectedException;
import com.smartlamp.agent.actions.ActionStatus;
import com.smartlamp.agent.actions.ActionType;
import com.smartlamp.agent.actions.AgentAction;
import com.smartlamp.agent.actions.AgentActionAuditService;
import com.smartlamp.agent.actions.DeviceControlExecutor;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.entity.DeviceCommand;
import com.smartlamp.entity.enums.CommandStatus;
import com.smartlamp.exception.BadRequestException;
import com.smartlamp.service.ConfigService;
import com.smartlamp.service.DeviceCommandService;
import com.smartlamp.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 控制意图工具单测（权限调整后）：
//  - 开灯/关灯：admin/operator 免确认自动执行（经 ActionGateway），如实返回执行结果；
//    municipal/无认证 → REJECTED_NO_PERMISSION；设备不存在/离线/已处于目标状态 → 拒绝
//  - 阈值/自动模式：仍需二次确认（PENDING + 确认接口），发起前同样校验角色
@ExtendWith(MockitoExtension.class)
class AgentActionToolsTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private AgentActionAuditService agentActionAuditService;

    @Mock
    private ConfigService configService;

    @Mock
    private DeviceCommandService deviceCommandService;

    private ActionManager actionManager;
    private AgentActionTools tools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        actionManager = new ActionManager();
        // 网关 + 真实控制执行器（ackTimeout 0 = 不等待回执，直接 COMMAND_ACCEPTED）
        ActionGateway actionGateway = new ActionGateway();
        ReflectionTestUtils.setField(actionGateway, "actionManager", actionManager);
        new DeviceControlExecutor(actionGateway, deviceCommandService, 0L);

        tools = new AgentActionTools();
        ReflectionTestUtils.setField(tools, "deviceService", deviceService);
        ReflectionTestUtils.setField(tools, "actionManager", actionManager);
        ReflectionTestUtils.setField(tools, "actionGateway", actionGateway);
        ReflectionTestUtils.setField(tools, "agentActionAuditService", agentActionAuditService);
        ReflectionTestUtils.setField(tools, "configService", configService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        AgentCallContext.clear();
    }

    private Device onlineDevice(String code) {
        Device device = new Device();
        device.setCode(code);
        device.setStatus("ONLINE");
        device.setLampStatus("OFF");
        return device;
    }

    // 设置认证上下文（默认 admin 角色，具备控制权限）
    private void setCurrentUser(String username) {
        setCurrentUser(username, "admin");
    }

    private void setCurrentUser(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private DeviceCommand dispatchedCommand(String commandId, String deviceCode, String action) {
        DeviceCommand command = new DeviceCommand();
        command.setCommandId(commandId);
        command.setDeviceCode(deviceCode);
        command.setAction(action);
        command.setStatus(CommandStatus.DISPATCHED);
        return command;
    }

    private LinkageConfigDTO linkage(boolean enabled, int threshold, int hysteresis) {
        LinkageConfigDTO dto = new LinkageConfigDTO();
        dto.setEnabled(enabled);
        dto.setThreshold(threshold);
        dto.setHysteresis(hysteresis);
        return dto;
    }

    // ============ 开灯/关灯：免确认自动执行 ============

    @Test
    void 在线设备请求关灯自动执行并如实报告未获回执() {
        Device lampOn = onlineDevice("lamp001");
        lampOn.setLampStatus("ON"); // 当前开启，关灯有实际效果
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(lampOn);
        when(deviceCommandService.dispatch("lamp001", "OFF", "AGENT"))
                .thenReturn(dispatchedCommand("CMD-1", "lamp001", "OFF"));
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        assertThat(result.path("source").asText()).isEqualTo("action");
        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.COMMAND_ACCEPTED.name());
        assertThat(result.path("actionId").asText()).isNotBlank();
        assertThat(result.path("message").asText()).contains("已执行").contains("尚未获得设备执行确认");
        verify(deviceCommandService).dispatch("lamp001", "OFF", "AGENT");
        // 发起者来自认证上下文
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getRequestedBy()).isEqualTo("admin");
        assertThat(action.getOriginalState()).isEqualTo("ON");
        assertThat(action.getTargetState()).isEqualTo("OFF");
        verify(agentActionAuditService).recordCreated(action);
    }

    @Test
    void 在线设备请求开灯自动执行() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));
        when(deviceCommandService.dispatch("lamp001", "ON", "AGENT"))
                .thenReturn(dispatchedCommand("CMD-2", "lamp001", "ON"));
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.COMMAND_ACCEPTED.name());
        verify(deviceCommandService).dispatch("lamp001", "ON", "AGENT");
    }

    @Test
    void 设备已处于目标状态拒绝不执行() {
        Device lampOn = onlineDevice("lamp001");
        lampOn.setLampStatus("ON");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(lampOn);
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_CHANGE");
        assertThat(result.path("actionId").asText()).isBlank();
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    // ============ 角色权限（与后端 @PreAuthorize 同一规则） ============

    @Test
    void municipal角色无控制权限被拒绝() {
        setCurrentUser("viewer", "municipal");

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_PERMISSION");
        assertThat(result.path("actionId").asText()).isBlank();
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    @Test
    void 无认证上下文无权限拒绝() {
        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_PERMISSION");
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    // ============ 设备检查 ============

    @Test
    void 不存在的设备拒绝且不执行() {
        when(deviceService.getDeviceByCode("lamp999")).thenReturn(null);
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp999"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_DEVICE_NOT_FOUND");
        assertThat(result.path("actionId").asText()).isBlank();
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    @Test
    void 离线设备拒绝且不执行() {
        Device device = onlineDevice("lamp003");
        device.setStatus("OFFLINE");
        when(deviceService.getDeviceByCode("lamp003")).thenReturn(device);
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", "lamp003"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_DEVICE_OFFLINE");
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    // ============ Tool 参数注入（阶段22） ============

    @Test
    void 超长deviceCode在创建层被拒绝且不创建Action() {
        String longCode = "x".repeat(200);
        when(deviceService.getDeviceByCode(longCode)).thenReturn(onlineDevice(longCode));
        setCurrentUser("admin");

        // 用开灯（目标 ON ≠ 当前 OFF）确保走到创建层长度校验
        assertThatThrownBy(() -> tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", longCode)))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("128");
        verify(agentActionAuditService, never()).recordCreated(any());
    }

    @Test
    void 注入式deviceId未匹配设备时拒绝不执行() {
        when(deviceService.getDeviceByCode("lamp001'; DROP TABLE device;--")).thenReturn(null);
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode()
                .put("deviceCode", "lamp001'; DROP TABLE device;--"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_DEVICE_NOT_FOUND");
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
    }

    @Test
    void 错误类型deviceCode被当作字符串且未匹配则拒绝() {
        when(deviceService.getDeviceByCode("123")).thenReturn(null);
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", 123));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_DEVICE_NOT_FOUND");
    }

    @Test
    void 多余字段被忽略且不进入Action参数() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));
        when(deviceCommandService.dispatch("lamp001", "ON", "AGENT"))
                .thenReturn(dispatchedCommand("CMD-3", "lamp001", "ON"));
        setCurrentUser("admin");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("deviceCode", "lamp001");
        args.put("command", "rm -rf /");
        args.put("topic", "device/+/cmd");

        JsonNode result = tools.requestTurnOn(args);

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.COMMAND_ACCEPTED.name());
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getArguments()).isEmpty(); // 多余字段不进入 Action 参数
    }

    @Test
    void 缺少设备编号报参数错误() {
        setCurrentUser("admin");
        assertThatThrownBy(() -> tools.requestTurnOff(objectMapper.createObjectNode()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deviceCode");
    }

    // ============ 来源会话关联（阶段30） ============

    @Test
    void 创建时记录来源会话conversationId() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));
        when(deviceCommandService.dispatch("lamp001", "ON", "AGENT"))
                .thenReturn(dispatchedCommand("CMD-4", "lamp001", "ON"));
        setCurrentUser("admin");
        AgentCallContext.setConversationId("conv-1");

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getConversationId()).isEqualTo("conv-1");
    }

    @Test
    void 无会话上下文时conversationId为空() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));
        when(deviceCommandService.dispatch("lamp001", "ON", "AGENT"))
                .thenReturn(dispatchedCommand("CMD-5", "lamp001", "ON"));
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getConversationId()).isNull();
    }

    // ============ 配置类请求（阶段20，仍需二次确认） ============

    @Test
    void 合法阈值生成待确认配置Action并快照状态() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));
        setCurrentUser("admin");

        JsonNode result = tools.requestSetThreshold(objectMapper.createObjectNode().put("value", 150));

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getActionType()).isEqualTo(ActionType.UPDATE_LUX_THRESHOLD);
        assertThat(action.getOriginalState()).contains("threshold\":30");
        assertThat(action.getTargetState()).isEqualTo("threshold=150");
        verify(agentActionAuditService).recordCreated(action);
    }

    @Test
    void 非法阈值拒绝且不创建Action() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));
        setCurrentUser("admin");

        JsonNode result = tools.requestSetThreshold(objectMapper.createObjectNode().put("value", 600));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_INVALID_VALUE");
        assertThat(result.path("actionId").asText()).isBlank();
    }

    @Test
    void 模糊阈值缺少数值报参数错误() {
        setCurrentUser("admin");
        assertThatThrownBy(() -> tools.requestSetThreshold(objectMapper.createObjectNode()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("候选值");
    }

    @Test
    void municipal角色无法发起配置修改() {
        setCurrentUser("viewer", "municipal");

        JsonNode result = tools.requestSetThreshold(objectMapper.createObjectNode().put("value", 150));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_PERMISSION");
        verify(configService, never()).getLinkageConfig();
    }

    @Test
    void 当前配置已是目标值无需修改() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));
        setCurrentUser("admin");

        JsonNode result = tools.requestSetThreshold(objectMapper.createObjectNode().put("value", 30));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_CHANGE");
    }

    @Test
    void 打开自动模式生成待确认Action() {
        when(configService.getLinkageConfig()).thenReturn(linkage(false, 30, 10));
        setCurrentUser("admin");

        JsonNode result = tools.requestSetAutoMode(objectMapper.createObjectNode().put("enabled", true));

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getActionType()).isEqualTo(ActionType.UPDATE_AUTO_MODE);
        assertThat(action.getTargetState()).isEqualTo("autoControl=true");
    }

    @Test
    void 关闭自动模式生成待确认Action() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));
        setCurrentUser("admin");

        JsonNode result = tools.requestSetAutoMode(objectMapper.createObjectNode().put("enabled", false));

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getTargetState()).isEqualTo("autoControl=false");
    }

    // ============ 批量开/关（权限调整后开放，均需二次确认） ============

    @Test
    void 批量关闭生成待确认请求且绝不自动执行() {
        when(deviceService.getAllDevices()).thenReturn(List.of(
                onlineDevice("lamp001"), onlineDevice("lamp002")));
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOffAll(objectMapper.createObjectNode());

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        assertThat(result.path("summary").asText()).contains("2 台在线");
        assertThat(result.path("expiresAt").asLong()).isPositive();
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getActionType()).isEqualTo(ActionType.TURN_OFF_ALL);
        assertThat(action.getTargetId()).isEqualTo("all");
        assertThat(action.getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
        // 绝不自动执行：等待用户按 actionId 确认
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
        verify(agentActionAuditService).recordCreated(action);
    }

    @Test
    void 批量关闭municipal无权限拒绝() {
        setCurrentUser("viewer", "municipal");

        JsonNode result = tools.requestTurnOffAll(objectMapper.createObjectNode());

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_PERMISSION");
        verify(deviceService, never()).getAllDevices();
    }

    @Test
    void 无在线设备时批量关闭拒绝() {
        when(deviceService.getAllDevices()).thenReturn(List.of());
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOffAll(objectMapper.createObjectNode());

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_TARGETS");
        assertThat(result.path("actionId").asText()).isBlank();
    }

    // ============ 批量开灯（对称开放，需二次确认） ============

    @Test
    void 批量开灯生成待确认请求且绝不自动执行() {
        when(deviceService.getAllDevices()).thenReturn(List.of(
                onlineDevice("lamp001"), onlineDevice("lamp002")));
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOnAll(objectMapper.createObjectNode());

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        assertThat(result.path("summary").asText()).contains("2 台在线");
        assertThat(result.path("expiresAt").asLong()).isPositive();
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getActionType()).isEqualTo(ActionType.TURN_ON_ALL);
        assertThat(action.getTargetId()).isEqualTo("all");
        assertThat(action.getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
        // 绝不自动执行：等待用户按 actionId 确认
        verify(deviceCommandService, never()).dispatch(any(), any(), any());
        verify(agentActionAuditService).recordCreated(action);
    }

    @Test
    void 批量开灯municipal无权限拒绝() {
        setCurrentUser("viewer", "municipal");

        JsonNode result = tools.requestTurnOnAll(objectMapper.createObjectNode());

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_PERMISSION");
        verify(deviceService, never()).getAllDevices();
    }

    @Test
    void 无在线设备时批量开灯拒绝() {
        when(deviceService.getAllDevices()).thenReturn(List.of());
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOnAll(objectMapper.createObjectNode());

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_TARGETS");
        assertThat(result.path("actionId").asText()).isBlank();
    }
}
