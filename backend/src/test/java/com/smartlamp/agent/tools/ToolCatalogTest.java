package com.smartlamp.agent.tools;

import com.smartlamp.agent.Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// 阶段22：工具白名单安全测试——Agent 可调用工具只有注册白名单，
// 不存在批量/万能命令/直接 MQTT 工具；未知工具返回结构化错误不执行任何操作
@ExtendWith(MockitoExtension.class)
class ToolCatalogTest {

    @Mock
    private AgentTools agentTools;

    @Mock
    private Retriever retriever;

    @Mock
    private AgentActionTools agentActionTools;

    private ToolCatalog toolCatalog;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        toolCatalog = new ToolCatalog();
        ReflectionTestUtils.setField(toolCatalog, "agentTools", agentTools);
        ReflectionTestUtils.setField(toolCatalog, "retriever", retriever);
        ReflectionTestUtils.setField(toolCatalog, "agentActionTools", agentActionTools);
    }

    @Test
    void 工具白名单只含已注册工具不含批量或万能命令() {
        List<String> names = toolCatalog.getSpecs().stream().map(ToolCatalog.ToolSpec::name).toList();

        assertThat(names).containsExactlyInAnyOrder(
                "search_knowledge", "get_device_list", "get_device_status", "get_latest_telemetry",
                "get_telemetry_history", "get_alert_history", "get_linkage_config",
                "turn_on_light", "turn_off_light", "turn_off_all", "turn_on_all",
                "set_light_threshold", "set_auto_mode");
        // 白名单外不存在：万能命令、直接 MQTT 工具
        assertThat(names).noneMatch(n -> n.contains("batch")
                || n.contains("execute") || n.contains("mqtt") || n.contains("command"));
    }

    @Test
    void 控制类工具均为低风险且确认要求与权限模型一致() {
        List<ToolCatalog.ToolSpec> actions = toolCatalog.getSpecs().stream()
                .filter(s -> "action".equals(s.source())).toList();

        assertThat(actions).hasSize(6);
        assertThat(actions).allMatch(s -> "LOW_WRITE".equals(s.riskLevel()));
        // 权限模型：单台开/关灯免二次确认（角色校验后直接执行），
        // 批量开/关与配置类修改必须用户在确认卡片确认
        assertThat(actions.stream().filter(s -> !s.requiresConfirmation())
                .map(ToolCatalog.ToolSpec::name).toList())
                .containsExactlyInAnyOrder("turn_on_light", "turn_off_light");
        assertThat(actions.stream().filter(ToolCatalog.ToolSpec::requiresConfirmation)
                .map(ToolCatalog.ToolSpec::name).toList())
                .containsExactlyInAnyOrder("turn_off_all", "turn_on_all",
                        "set_light_threshold", "set_auto_mode");
    }

    @Test
    void 未知工具返回结构化错误不执行任何操作() {
        ObjectNode result = toolCatalog.execute("execute_command", objectMapper.createObjectNode());
        assertThat(result.path("error").asText()).contains("未知工具");

        // "批量修改设备"等高风险批量操作未注册为工具（不开放）
        ObjectNode batch = toolCatalog.execute("batch_update_devices",
                objectMapper.createObjectNode().put("deviceCode", "all"));
        assertThat(batch.path("error").asText()).contains("未知工具");
    }

    @Test
    void 设备列表结果透出灯开关状态() {
        var device = new com.smartlamp.dto.DeviceDTO(1L, "lamp001", "北门", "ONLINE", 120.0, 1700000000000L);
        device.setLampStatus("ON");
        device.setBound(true);
        when(agentTools.getDeviceList()).thenReturn(List.of(device));

        ObjectNode result = toolCatalog.execute("get_device_list", objectMapper.createObjectNode());

        assertThat(result.path("source").asText()).isEqualTo("system_data");
        assertThat(result.path("devices").get(0).path("lampStatus").asText()).isEqualTo("ON");
        assertThat(result.path("devices").get(0).path("bound").asBoolean()).isTrue();
        assertThat(result.path("devices").get(0).path("statusText").asText()).isEqualTo("ONLINE·灯开");
    }

    @Test
    void 设备列表结果附带统计汇总() {
        var on = new com.smartlamp.dto.DeviceDTO(1L, "lamp001", "北门", "ONLINE", 120.0, 1700000000000L);
        on.setLampStatus("ON");
        var off = new com.smartlamp.dto.DeviceDTO(2L, "lamp002", "东门", "OFFLINE", 110.0, 1700000000000L);
        off.setLampStatus("OFF");
        when(agentTools.getDeviceList()).thenReturn(List.of(on, off));

        ObjectNode result = toolCatalog.execute("get_device_list", objectMapper.createObjectNode());

        assertThat(result.path("summary").asText())
                .contains("共2台设备")
                .contains("在线1台（lamp001）")
                .contains("离线1台（lamp002）")
                .contains("灯打开1台（lamp001）")
                .contains("灯关闭1台（lamp002）");
    }
}
