package com.smartlamp.agent.tools;

import com.smartlamp.agent.actions.ActionManager;
import com.smartlamp.agent.actions.ActionStatus;
import com.smartlamp.agent.actions.ActionType;
import com.smartlamp.agent.actions.AgentAction;
import com.smartlamp.entity.Device;
import com.smartlamp.exception.BadRequestException;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 阶段16：控制意图工具单测——只生成待确认 Action，绝不执行控制
@ExtendWith(MockitoExtension.class)
class AgentActionToolsTest {

    @Mock
    private DeviceService deviceService;

    private ActionManager actionManager;
    private AgentActionTools tools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        actionManager = new ActionManager();
        tools = new AgentActionTools();
        ReflectionTestUtils.setField(tools, "deviceService", deviceService);
        ReflectionTestUtils.setField(tools, "actionManager", actionManager);
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
}
