package com.smartlamp.agent.actions;

import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Device;
import com.smartlamp.service.ConfigService;
import com.smartlamp.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 阶段17/20：用户确认/取消业务单测——归属与角色校验（阶段32 安全修复#6）+ 确认时二次校验 + 状态机验证
// （纯内存 + Mockito，不依赖 Spring/MySQL/MQTT）
@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    // 测试固定身份：发起者 test-user（admin 角色），发起操作后由其本人确认
    private static final String OWNER = "test-user";
    private static final String ROLE_ADMIN = "admin";

    @Mock
    private DeviceService deviceService;

    @Mock
    private ConfigService configService;

    private ActionManager actionManager;
    private ActionGateway actionGateway;
    private ActionService actionService;
    private AtomicInteger executorCalls;

    @BeforeEach
    void setUp() {
        actionManager = new ActionManager();
        actionGateway = new ActionGateway();
        ReflectionTestUtils.setField(actionGateway, "actionManager", actionManager);

        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "actionManager", actionManager);
        ReflectionTestUtils.setField(actionService, "actionGateway", actionGateway);
        ReflectionTestUtils.setField(actionService, "deviceService", deviceService);
        ReflectionTestUtils.setField(actionService, "configService", configService);

        // 注册测试执行器（计数观察是否被调用；返回 null = 不报告结果，按默认成功处理）
        executorCalls = new AtomicInteger();
        actionGateway.registerExecutor(ActionType.TURN_ON_LIGHT, action -> {
            // 状态机中间态验证：执行器被调用时 Action 必须处于 EXECUTING
            assertThat(action.getStatus()).isEqualTo(ActionStatus.EXECUTING);
            executorCalls.incrementAndGet();
            return null;
        });
        actionGateway.registerExecutor(ActionType.TURN_OFF_LIGHT, action -> {
            executorCalls.incrementAndGet();
            return null;
        });
    }

    private Device onlineDevice(String code, String lampStatus) {
        Device device = new Device();
        device.setCode(code);
        device.setStatus("ONLINE");
        device.setLampStatus(lampStatus);
        return device;
    }

    private AgentAction createAction(ActionType type, String code) {
        return actionManager.create(type, "device", code, Map.of(), OWNER);
    }

    private LinkageConfigDTO linkage(boolean enabled, int threshold, int hysteresis) {
        LinkageConfigDTO dto = new LinkageConfigDTO();
        dto.setEnabled(enabled);
        dto.setThreshold(threshold);
        dto.setHysteresis(hysteresis);
        return dto;
    }

    private AgentAction createConfigAction(ActionType type, Map<String, Object> args) {
        return actionManager.create(type, "config", "system", args, OWNER);
    }

    // ============ 正常确认：完整状态机 PENDING → CONFIRMED → EXECUTING → SUCCESS ============

    @Test
    void 正常确认开灯走完整状态机并执行成功() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001", "OFF"));
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001");

        AgentAction result = actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN);

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(result.getMessage()).contains("执行成功");
        assertThat(executorCalls).hasValue(1); // 执行器被调用，且调用时状态为 EXECUTING
    }

    @Test
    void 正常确认关灯执行成功() {
        when(deviceService.getDeviceByCode("lamp002")).thenReturn(onlineDevice("lamp002", "ON"));
        AgentAction action = createAction(ActionType.TURN_OFF_LIGHT, "lamp002");

        AgentAction result = actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN);

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(executorCalls).hasValue(1);
    }

    @Test
    void operator角色同样具备控制权限() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001", "OFF"));
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001");

        AgentAction result = actionService.confirmAndExecute(action.getActionId(), OWNER, "operator");

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(executorCalls).hasValue(1);
    }

    // ============ 归属与角色校验（安全修复：Action ID 泄露防护） ============

    @Test
    void 他人无法确认且不泄露操作存在性() {
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001");

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), "other-user", ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("不属于当前用户");
        // 状态仍为 PENDING，执行器零调用
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.PENDING_CONFIRMATION);
        assertThat(executorCalls).hasValue(0);
    }

    @Test
    void 他人无法取消() {
        AgentAction action = createAction(ActionType.TURN_OFF_LIGHT, "lamp001");

        assertThatThrownBy(() -> actionService.cancel(action.getActionId(), "other-user", ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("不属于当前用户");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.PENDING_CONFIRMATION);
    }

    @Test
    void municipal角色无控制权限被拒() {
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001");

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), OWNER, "municipal"))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("无控制权限");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.PENDING_CONFIRMATION);
        assertThat(executorCalls).hasValue(0);
    }

    // ============ 用户取消 ============

    @Test
    void 用户取消后Action置CANCELLED且执行器零调用() {
        AgentAction action = createAction(ActionType.TURN_OFF_LIGHT, "lamp001");

        AgentAction result = actionService.cancel(action.getActionId(), OWNER, ROLE_ADMIN);

        assertThat(result.getStatus()).isEqualTo(ActionStatus.CANCELLED);
        assertThat(executorCalls).hasValue(0);
    }

    // ============ 重复确认 / 重复取消 ============

    @Test
    void 已执行Action重复确认被拒绝且不二次执行() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001", "OFF"));
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001");
        actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN);

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("重复");
        // 状态仍为 SUCCESS，执行器只被调用过一次
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.SUCCESS);
        assertThat(executorCalls).hasValue(1);
    }

    @Test
    void 已取消Action重复取消被拒绝() {
        AgentAction action = createAction(ActionType.TURN_OFF_LIGHT, "lamp001");
        actionService.cancel(action.getActionId(), OWNER, ROLE_ADMIN);

        assertThatThrownBy(() -> actionService.cancel(action.getActionId(), OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("重复");
    }

    // ============ 过期确认 ============

    @Test
    void 过期Action确认被拒绝并置EXPIRED() throws InterruptedException {
        AgentAction action = actionManager.create(ActionType.TURN_OFF_LIGHT, "device", "lamp001",
                Map.of(), OWNER, 1L);
        Thread.sleep(10);

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("过期");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.EXPIRED);
        assertThat(executorCalls).hasValue(0);
    }

    // ============ 不存在的 actionId ============

    @Test
    void 不存在的actionId确认和取消均被拒绝() {
        assertThatThrownBy(() -> actionService.confirmAndExecute("no-such-id", OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> actionService.cancel("no-such-id", OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("不存在");
    }

    // ============ 确认时设备二次校验 ============

    @Test
    void 确认时设备已被删除则拒绝并置FAILED() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(null);
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001");

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("设备不存在");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.FAILED);
        assertThat(executorCalls).hasValue(0);
    }

    @Test
    void 确认时设备已离线则拒绝并置FAILED() {
        Device offline = onlineDevice("lamp001", "OFF");
        offline.setStatus("OFFLINE");
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(offline);
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001");

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("离线");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.FAILED);
        assertThat(executorCalls).hasValue(0);
    }

    @Test
    void 确认前设备状态已变化开灯请求时已ON则拒绝() {
        when(deviceService.getDeviceByCode("lamp001")).thenReturn(onlineDevice("lamp001", "ON"));
        AgentAction action = createAction(ActionType.TURN_ON_LIGHT, "lamp001");

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("状态已变化");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.FAILED);
        assertThat(executorCalls).hasValue(0); // 未执行任何控制
    }

    @Test
    void 确认前设备状态已变化关灯请求时已OFF则拒绝() {
        when(deviceService.getDeviceByCode("lamp002")).thenReturn(onlineDevice("lamp002", "OFF"));
        AgentAction action = createAction(ActionType.TURN_OFF_LIGHT, "lamp002");

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("状态已变化");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.FAILED);
        assertThat(executorCalls).hasValue(0);
    }

    // ============ 配置类目标（阶段20） ============

    @Test
    void 配置类确认成功跳过设备检查并执行() {
        actionGateway.registerExecutor(ActionType.UPDATE_LUX_THRESHOLD, action -> {
            executorCalls.incrementAndGet();
            return null;
        });
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));
        AgentAction action = createConfigAction(ActionType.UPDATE_LUX_THRESHOLD, Map.of("value", 150));

        AgentAction result = actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN);

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(executorCalls).hasValue(1);
        // 配置类目标不检查设备
        verify(deviceService, never()).getDeviceByCode(any());
    }

    @Test
    void 确认时配置已是目标值拒绝() {
        actionGateway.registerExecutor(ActionType.UPDATE_LUX_THRESHOLD, action -> {
            executorCalls.incrementAndGet();
            return null;
        });
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 150, 10));
        AgentAction action = createConfigAction(ActionType.UPDATE_LUX_THRESHOLD, Map.of("value", 150));

        assertThatThrownBy(() -> actionService.confirmAndExecute(action.getActionId(), OWNER, ROLE_ADMIN))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("已变化");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.FAILED);
        assertThat(executorCalls).hasValue(0);
    }

    @Test
    void 配置类取消确认() {
        AgentAction action = createConfigAction(ActionType.UPDATE_AUTO_MODE, Map.of("enabled", false));

        AgentAction result = actionService.cancel(action.getActionId(), OWNER, ROLE_ADMIN);

        assertThat(result.getStatus()).isEqualTo(ActionStatus.CANCELLED);
        assertThat(executorCalls).hasValue(0);
    }
}
