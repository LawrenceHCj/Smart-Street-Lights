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
                "turn_on_light", "turn_off_light", "turn_off_all", "set_light_threshold", "set_auto_mode");
        // 白名单外不存在：万能命令、直接 MQTT 工具
        assertThat(names).noneMatch(n -> n.contains("batch")
                || n.contains("execute") || n.contains("mqtt") || n.contains("command"));
    }

    @Test
    void 控制类工具均要求确认且为低风险() {
        List<ToolCatalog.ToolSpec> actions = toolCatalog.getSpecs().stream()
                .filter(s -> "action".equals(s.source())).toList();

        assertThat(actions).hasSize(5);
        assertThat(actions).allMatch(s -> "LOW_WRITE".equals(s.riskLevel()) && s.requiresConfirmation());
    }

    @Test
    void 未知工具返回结构化错误不执行任何操作() {
        ObjectNode result = toolCatalog.execute("execute_command", objectMapper.createObjectNode());
        assertThat(result.path("error").asText()).contains("未知工具");

        // "全部打开"未注册（批量开灯不开放）
        ObjectNode batch = toolCatalog.execute("turn_on_all",
                objectMapper.createObjectNode().put("deviceCode", "all"));
        assertThat(batch.path("error").asText()).contains("未知工具");
    }
}
