package com.smartlamp.agent.actions;

import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 阶段20：配置控制执行器单测——白名单配置 Action → 3号 ConfigService 的映射与如实报告
@ExtendWith(MockitoExtension.class)
class ConfigControlExecutorTest {

    @Mock
    private ConfigService configService;

    private ActionManager actionManager;
    private ActionGateway actionGateway;

    @BeforeEach
    void setUp() {
        actionManager = new ActionManager();
        actionGateway = new ActionGateway();
        ReflectionTestUtils.setField(actionGateway, "actionManager", actionManager);
        new ConfigControlExecutor(actionGateway, configService);
    }

    private LinkageConfigDTO linkage(boolean enabled, int threshold, int hysteresis) {
        LinkageConfigDTO dto = new LinkageConfigDTO();
        dto.setEnabled(enabled);
        dto.setThreshold(threshold);
        dto.setHysteresis(hysteresis);
        return dto;
    }

    private AgentAction confirmedAction(ActionType type, Map<String, Object> args) {
        AgentAction action = actionManager.create(type, "config", "system", args, "test-user");
        actionManager.confirm(action.getActionId());
        return action;
    }

    // ============ 合法阈值 ============

    @Test
    void 合法阈值保存并保持其他配置字段不变() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));
        AgentAction action = confirmedAction(ActionType.UPDATE_LUX_THRESHOLD, Map.of("value", 150));

        AgentAction result = actionGateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.COMMAND_ACCEPTED);
        assertThat(result.getMessage()).contains("COMMAND_ACCEPTED").contains("threshold=150");
        ArgumentCaptor<LinkageConfigDTO> captor = ArgumentCaptor.forClass(LinkageConfigDTO.class);
        verify(configService).saveLinkageConfig(captor.capture());
        assertThat(captor.getValue().getThreshold()).isEqualTo(150);
        assertThat(captor.getValue().isEnabled()).isTrue();   // 其余字段保持不变
        assertThat(captor.getValue().getHysteresis()).isEqualTo(10);
    }

    // ============ 打开 / 关闭自动模式 ============

    @Test
    void 打开自动模式() {
        when(configService.getLinkageConfig()).thenReturn(linkage(false, 30, 10));
        AgentAction action = confirmedAction(ActionType.UPDATE_AUTO_MODE, Map.of("enabled", true));

        AgentAction result = actionGateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.COMMAND_ACCEPTED);
        ArgumentCaptor<LinkageConfigDTO> captor = ArgumentCaptor.forClass(LinkageConfigDTO.class);
        verify(configService).saveLinkageConfig(captor.capture());
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getThreshold()).isEqualTo(30);
    }

    @Test
    void 关闭自动模式() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));
        AgentAction action = confirmedAction(ActionType.UPDATE_AUTO_MODE, Map.of("enabled", false));

        AgentAction result = actionGateway.execute(action.getActionId());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.COMMAND_ACCEPTED);
        ArgumentCaptor<LinkageConfigDTO> captor = ArgumentCaptor.forClass(LinkageConfigDTO.class);
        verify(configService).saveLinkageConfig(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    // ============ config Service 失败 ============

    @Test
    void configService失败时Action置FAILED() {
        when(configService.getLinkageConfig()).thenReturn(linkage(true, 30, 10));
        org.mockito.Mockito.doThrow(new RuntimeException("配置服务不可用"))
                .when(configService).saveLinkageConfig(any());
        AgentAction action = confirmedAction(ActionType.UPDATE_LUX_THRESHOLD, Map.of("value", 150));

        assertThatThrownBy(() -> actionGateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class)
                .hasMessageContaining("FAILED");
        assertThat(actionManager.find(action.getActionId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.FAILED);
    }

    // ============ 安全红线 ============

    @Test
    void 未确认Action绝不调用configService() {
        AgentAction action = actionManager.create(ActionType.UPDATE_AUTO_MODE, "config", "system",
                Map.of("enabled", true), "test-user");

        assertThatThrownBy(() -> actionGateway.execute(action.getActionId()))
                .isInstanceOf(ActionRejectedException.class);
        verify(configService, never()).saveLinkageConfig(any());
        verify(configService, never()).getLinkageConfig();
    }
}
