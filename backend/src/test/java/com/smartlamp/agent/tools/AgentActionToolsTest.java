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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

// 阶段16：控制意图工具单测——只生成待确认 Action，绝不执行控制
@ExtendWith(MockitoExtension.class)
class AgentActionToolsTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private AgentActionAuditService agentActionAuditService;

    @Mock
    private ConfigService configService;

    private ActionManager actionManager;
    private AgentActionTools tools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        actionManager = new ActionManager();
        tools = new AgentActionTools();
        ReflectionTestUtils.setField(tools, "deviceService", deviceService);
        ReflectionTestUtils.setField(tools, "actionManager", actionManager);
        ReflectionTestUtils.setField(tools, "agentActionAuditService", agentActionAuditService);
        ReflectionTestUtils.setField(tools, "configService", configService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Device onlineDevice(String code) {
        Device device = new Device();
        device.setCode(code);
        device.setStatus("ONLINE");
        device.setLampStatus("OFF");
        return device;
    }

    private void setCurrentUser(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    // ============ 关闭 lamp001 ============

    @Test
    void 在线设备请求关灯生成待确认Action且不执行控制() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));
        setCurrentUser("admin");

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        assertThat(result.path("source").asText()).isEqualTo("action");
        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        assertThat(result.path("actionId").asText()).isNotBlank();
        assertThat(result.path("riskLevel").asText()).isEqualTo("LOW_WRITE");
        assertThat(result.path("lampStatus").asText()).isEqualTo("OFF");
        assertThat(result.path("message").asText()).contains("待确认");

        // Action 已入管理器且为待确认状态，发起者来自认证上下文
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getActionType()).isEqualTo(ActionType.TURN_OFF_LIGHT);
        assertThat(action.getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
        assertThat(action.getTargetId()).isEqualTo("lamp001");
        assertThat(action.getRequestedBy()).isEqualTo("admin");
        // 状态快照与创建审计（阶段19）
        assertThat(action.getOriginalState()).isEqualTo("OFF");
        assertThat(action.getTargetState()).isEqualTo("OFF");
        verify(agentActionAuditService).recordCreated(action);
    }

    // ============ 打开 lamp001 ============

    @Test
    void 在线设备请求开灯生成待确认Action() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getActionType()).isEqualTo(ActionType.TURN_ON_LIGHT);
        assertThat(action.getStatus()).isEqualTo(ActionStatus.PENDING_CONFIRMATION);
    }

    // ============ 不存在的设备 ============

    @Test
    void 不存在的设备拒绝且不创建Action() {
        when(deviceService.getDeviceByCode("lamp999")).thenReturn(null);

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp999"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_DEVICE_NOT_FOUND");
        assertThat(result.path("actionId").asText()).isBlank();
        assertThat(result.path("message").asText()).contains("不存在");
    }

    // ============ 离线设备 ============

    @Test
    void 离线设备拒绝且不创建Action() {
        Device device = onlineDevice("lamp003");
        device.setStatus("OFFLINE");
        when(deviceService.getDeviceByCode("lamp003")).thenReturn(device);

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", "lamp003"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_DEVICE_OFFLINE");
        assertThat(result.path("actionId").asText()).isBlank();
        assertThat(result.path("message").asText()).contains("离线");
    }

    // ============ 参数与身份 ============

    @Test
    void 缺少设备编号报参数错误() {
        assertThatThrownBy(() -> tools.requestTurnOff(objectMapper.createObjectNode()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deviceCode");
    }

    @Test
    void 无认证上下文时发起者记为unknown() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getRequestedBy()).isEqualTo("unknown");
    }

    // ============ 配置类请求（阶段20） ============

    private LinkageConfigDTO linkage(boolean enabled, int threshold, int hysteresis) {
        LinkageConfigDTO dto = new LinkageConfigDTO();
        dto.setEnabled(enabled);
        dto.setThreshold(threshold);
        dto.setHysteresis(hysteresis);
        return dto;
    }

    @Test
    void 合法阈值生成待确认配置Action并快照状态() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));

        JsonNode result = tools.requestSetThreshold(objectMapper.createObjectNode().put("value", 150));

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        assertThat(result.path("currentConfig").asText()).contains("threshold\":30");
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getActionType()).isEqualTo(ActionType.UPDATE_LUX_THRESHOLD);
        assertThat(action.getTargetType()).isEqualTo("config");
        assertThat(action.getTargetId()).isEqualTo("system");
        assertThat(action.getOriginalState()).contains("threshold\":30");
        assertThat(action.getTargetState()).isEqualTo("threshold=150");
        verify(agentActionAuditService).recordCreated(action);
    }

    @Test
    void 非法阈值拒绝且不创建Action() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));

        JsonNode result = tools.requestSetThreshold(objectMapper.createObjectNode().put("value", 600));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_INVALID_VALUE");
        assertThat(result.path("actionId").asText()).isBlank();
        assertThat(result.path("message").asText()).contains("10-500");
    }

    @Test
    void 模糊阈值缺少数值报参数错误() {
        assertThatThrownBy(() -> tools.requestSetThreshold(objectMapper.createObjectNode()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("候选值");
    }

    @Test
    void 当前配置已是目标值无需修改() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));

        JsonNode result = tools.requestSetThreshold(objectMapper.createObjectNode().put("value", 30));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_NO_CHANGE");
        assertThat(result.path("actionId").asText()).isBlank();
    }

    @Test
    void 打开自动模式生成待确认Action() {
        when(configService.getLinkageConfig()).thenReturn(linkage(false, 30, 10));

        JsonNode result = tools.requestSetAutoMode(objectMapper.createObjectNode().put("enabled", true));

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getActionType()).isEqualTo(ActionType.UPDATE_AUTO_MODE);
        assertThat(action.getTargetState()).isEqualTo("autoControl=true");
    }

    @Test
    void 关闭自动模式生成待确认Action() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));

        JsonNode result = tools.requestSetAutoMode(objectMapper.createObjectNode().put("enabled", false));

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getTargetState()).isEqualTo("autoControl=false");
    }

    // ============ 来源会话关联（阶段30） ============

    @Test
    void 创建时记录来源会话conversationId() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));
        AgentCallContext.setConversationId("conv-1");
        try {
            JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

            AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
            assertThat(action.getConversationId()).isEqualTo("conv-1");
        } finally {
            AgentCallContext.clear();
        }
    }

    @Test
    void 无会话上下文时conversationId为空() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", "lamp001"));

        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getConversationId()).isNull();
    }

    // ============ 阶段22：Tool 参数注入 ============

    @Test
    void 超长deviceCode在创建层被拒绝且不创建Action() {
        String longCode = "x".repeat(200);
        // 即使设备查找命中，创建层仍按长度上限拒绝
        when(deviceService.getDeviceByCode(longCode)).thenReturn(onlineDevice(longCode));

        assertThatThrownBy(() -> tools.requestTurnOff(objectMapper.createObjectNode().put("deviceCode", longCode)))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("128");
        verify(agentActionAuditService, never()).recordCreated(any());
    }

    @Test
    void 注入式deviceId未匹配设备时拒绝不创建Action() {
        when(deviceService.getDeviceByCode("lamp001'; DROP TABLE device;--")).thenReturn(null);

        JsonNode result = tools.requestTurnOff(objectMapper.createObjectNode()
                .put("deviceCode", "lamp001'; DROP TABLE device;--"));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_DEVICE_NOT_FOUND");
        assertThat(result.path("actionId").asText()).isBlank();
        verify(agentActionAuditService, never()).recordCreated(any());
    }

    @Test
    void 错误类型deviceCode被当作字符串且未匹配则拒绝() {
        when(deviceService.getDeviceByCode("123")).thenReturn(null);

        JsonNode result = tools.requestTurnOn(objectMapper.createObjectNode().put("deviceCode", 123));

        assertThat(result.path("status").asText()).isEqualTo("REJECTED_DEVICE_NOT_FOUND");
        assertThat(result.path("actionId").asText()).isBlank();
    }

    @Test
    void 多余字段被忽略且不进入Action参数() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001"));
        ObjectNode args = objectMapper.createObjectNode();
        args.put("deviceCode", "lamp001");
        args.put("command", "rm -rf /");
        args.put("topic", "device/+/cmd");

        JsonNode result = tools.requestTurnOff(args);

        assertThat(result.path("status").asText()).isEqualTo(ActionStatus.PENDING_CONFIRMATION.name());
        AgentAction action = actionManager.find(result.path("actionId").asText()).orElseThrow();
        assertThat(action.getArguments()).isEmpty(); // 多余字段不进入 Action 参数
    }
}
