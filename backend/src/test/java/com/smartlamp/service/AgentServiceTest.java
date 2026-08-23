package com.smartlamp.service;

import com.smartlamp.agent.KnowledgeBase;
import com.smartlamp.agent.LlmClient;
import com.smartlamp.agent.LlmException;
import com.smartlamp.agent.PromptProvider;
import com.smartlamp.agent.Retriever;
import com.smartlamp.agent.tools.AgentTools;
import com.smartlamp.agent.tools.ToolCatalog;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.entity.Alarm;
import com.smartlamp.exception.BadRequestException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import tools.jackson.databind.node.ArrayNode;

// 覆盖阶段8要求的场景：普通维修问题 / 当前设备状态 / 历史告警 / 综合故障分析 / 不存在的设备 / 系统数据不可用，
// 以及本地模式与失败降级。LLM 用 Mockito 脚本化两轮响应（第一轮工具调用、第二轮最终回答）。
@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private DeviceService deviceService;
    @Mock
    private LightService lightService;
    @Mock
    private AlarmService alarmService;
    @Mock
    private ConfigService configService;

    private AgentService agentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Retriever retriever = new Retriever();
        ReflectionTestUtils.setField(retriever, "knowledgeBase", new KnowledgeBase());

        AgentTools agentTools = new AgentTools();
        ReflectionTestUtils.setField(agentTools, "deviceService", deviceService);
        ReflectionTestUtils.setField(agentTools, "lightService", lightService);
        ReflectionTestUtils.setField(agentTools, "alarmService", alarmService);
        ReflectionTestUtils.setField(agentTools, "configService", configService);

        ToolCatalog toolCatalog = new ToolCatalog();
        ReflectionTestUtils.setField(toolCatalog, "agentTools", agentTools);
        ReflectionTestUtils.setField(toolCatalog, "retriever", retriever);

        agentService = new AgentService();
        ReflectionTestUtils.setField(agentService, "retriever", retriever);
        ReflectionTestUtils.setField(agentService, "promptProvider", new PromptProvider());
        ReflectionTestUtils.setField(agentService, "llmClient", llmClient);
        ReflectionTestUtils.setField(agentService, "toolCatalog", toolCatalog);
    }

    // ============ 本地模式（未配置大模型） ============

    @Test
    void 未配置大模型时离线问题走本地知识库() {
        AskResponse result = agentService.ask("路灯离线应该怎么排查？");

        assertThat(result.getAnswer()).contains("心跳");
        assertThat(result.getSources()).isNotEmpty();
        assertThat(result.getSources().get(0).getTitle()).isEqualTo("设备离线排查");
    }

    @Test
    void 未配置大模型时无命中返回提示() {
        AskResponse result = agentService.ask("今天天气怎么样？");

        assertThat(result.getAnswer()).contains("未找到");
        assertThat(result.getSources()).isEmpty();
    }

    @Test
    void 空问题抛出400异常() {
        assertThatThrownBy(() -> agentService.ask(""))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("question 不能为空");
        assertThatThrownBy(() -> agentService.ask(null))
                .isInstanceOf(BadRequestException.class);
    }

    // ============ 场景1：普通维修问题 → 知识工具 ============

    @Test
    void 普通维修问题使用知识工具回答() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenReturn(responseWithToolCalls(toolCall("call-1", "search_knowledge", "{\"query\":\"路灯离线\"}")))
                .thenReturn(response("根据《设备离线排查》知识库信息：先确认设备供电与网关连接，再检查最近心跳时间。"));

        AskResponse result = agentService.ask("路灯离线应该怎么处理？");

        assertThat(result.getAnswer()).contains("设备离线排查");
        // "路灯离线"命中两条知识（设备离线排查 + 设备状态异常），均来自知识库
        assertThat(result.getSources()).hasSize(2);
        assertThat(result.getSources()).anyMatch(s -> "设备离线排查".equals(s.getTitle()));
        assertThat(result.getSources()).allMatch(s -> "knowledge".equals(s.getSection()));
    }

    // ============ 场景2：当前设备状态 → 设备工具 ============

    @Test
    void 设备状态问题使用设备工具回答() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(deviceService.getAllDeviceDTOs()).thenReturn(List.of(
                new DeviceDTO(1L, "lamp001", "北门", "ONLINE", 82.5, 1700000000000L)));
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenReturn(responseWithToolCalls(toolCall("call-1", "get_device_status", "{\"deviceCode\":\"lamp001\"}")))
                .thenReturn(response("系统实时数据：lamp001 当前在线。"));

        AskResponse result = agentService.ask("lamp001现在在线吗？");

        assertThat(result.getAnswer()).contains("在线");
        assertThat(result.getSources()).hasSize(1);
        assertThat(result.getSources().get(0).getSection()).isEqualTo("system_data");
        assertThat(result.getSources().get(0).getTitle()).contains("系统实时数据");
    }

    // ============ 场景3：历史告警问题 → 告警工具 ============

    @Test
    void 历史告警问题使用告警工具回答() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        Alarm alarm = new Alarm();
        alarm.setId(1L);
        alarm.setDeviceId("lamp003");
        alarm.setType("离线");
        alarm.setLevel("warning");
        alarm.setMessage("设备心跳中断超过阈值时间");
        alarm.setStatus("OPEN");
        when(alarmService.getAllAlarms()).thenReturn(List.of(alarm));
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenReturn(responseWithToolCalls(toolCall("call-1", "get_alert_history", "{\"deviceCode\":\"lamp003\"}")))
                .thenReturn(response("系统实时数据：lamp003 有一条离线告警。"));

        AskResponse result = agentService.ask("lamp003最近有什么告警？");

        assertThat(result.getAnswer()).contains("告警");
        assertThat(result.getSources()).hasSize(1);
        assertThat(result.getSources().get(0).getSection()).isEqualTo("system_data");
    }

    // ============ 场景4：综合故障分析 → 多工具组合（设备 + 告警 + 知识） ============

    @Test
    void 综合故障分析组合多个工具回答() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(deviceService.getAllDeviceDTOs()).thenReturn(List.of(
                new DeviceDTO(3L, "lamp003", "东门", "OFFLINE", 40.0, 1699990000000L)));
        Alarm alarm = new Alarm();
        alarm.setId(2L);
        alarm.setDeviceId("lamp003");
        alarm.setType("离线");
        alarm.setStatus("OPEN");
        when(alarmService.getAllAlarms()).thenReturn(List.of(alarm));
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenReturn(responseWithToolCalls(
                        toolCall("call-1", "get_device_status", "{\"deviceCode\":\"lamp003\"}"),
                        toolCall("call-2", "get_alert_history", "{\"deviceCode\":\"lamp003\"}"),
                        toolCall("call-3", "search_knowledge", "{\"query\":\"设备离线排查\"}")))
                .thenReturn(response("综合判断：lamp003 当前离线，有1条离线告警；建议按《设备离线排查》检查供电与网关。"));

        AskResponse result = agentService.ask("lamp003最近为什么经常离线？");

        assertThat(result.getAnswer()).contains("lamp003");
        // 知识检索命中2条 + 设备状态/告警记录2个系统数据来源 = 4 个来源
        assertThat(result.getSources()).hasSize(4);
        assertThat(result.getSources()).anyMatch(s -> "设备离线排查".equals(s.getTitle()));
        assertThat(result.getSources()).filteredOn(s -> "system_data".equals(s.getSection())).hasSize(2);
    }

    // ============ 场景5：不存在的设备ID ============

    @Test
    void 查询不存在的设备不崩溃并如实回答() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(deviceService.getAllDeviceDTOs()).thenReturn(List.of());
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenReturn(responseWithToolCalls(toolCall("call-1", "get_device_status", "{\"deviceCode\":\"lamp999\"}")))
                .thenReturn(response("系统实时数据：未找到编号为 lamp999 的设备。"));

        AskResponse result = agentService.ask("lamp999现在在线吗？");

        assertThat(result.getAnswer()).contains("未找到");
        assertThat(result.getSources()).hasSize(1);
        assertThat(result.getSources().get(0).getSection()).isEqualTo("system_data");
    }

    // ============ 场景6：系统数据不可用 ============

    @Test
    void 系统数据查询失败时不崩溃并如实告知() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(deviceService.getAllDeviceDTOs()).thenThrow(new RuntimeException("数据库连接失败"));
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenReturn(responseWithToolCalls(toolCall("call-1", "get_device_status", "{\"deviceCode\":\"lamp001\"}")))
                .thenReturn(response("系统实时数据暂时不可用，请稍后重试。"));

        AskResponse result = agentService.ask("lamp001现在在线吗？");

        assertThat(result.getAnswer()).contains("不可用");
        assertThat(result.getSources()).hasSize(1);
        assertThat(result.getSources().get(0).getSection()).isEqualTo("system_data");
    }

    // ============ 模型不调工具 / 流程失败降级 ============

    @Test
    void 模型直接回答不调工具时正常返回() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenReturn(response("这是模型直接给出的回答。"));

        AskResponse result = agentService.ask("路灯维护要注意什么安全事项？");

        assertThat(result.getAnswer()).isEqualTo("这是模型直接给出的回答。");
        assertThat(result.getSources()).isNotEmpty(); // 未调工具时附带本地检索来源
    }

    @Test
    void 模型调用失败时降级为本地知识库回答() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenThrow(new LlmException("LLM API 返回 500"));

        AskResponse result = agentService.ask("路灯离线应该怎么排查？");

        assertThat(result.getAnswer()).contains("心跳");
        assertThat(result.getSources().get(0).getTitle()).isEqualTo("设备离线排查");
    }

    // ============ 工具执行回填验证 ============

    @Test
    void 工具结果会回填给模型二次调用() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(deviceService.getAllDeviceDTOs()).thenReturn(List.of(
                new DeviceDTO(1L, "lamp001", "北门", "ONLINE", 82.5, 1700000000000L)));
        when(llmClient.completeChat(anyList(), any(ArrayNode.class)))
                .thenReturn(responseWithToolCalls(toolCall("call-1", "get_device_status", "{\"deviceCode\":\"lamp001\"}")))
                .thenReturn(response("lamp001 在线"));

        agentService.ask("lamp001现在在线吗？");

        // 第二次调用的消息里必须包含工具结果（role=tool、带 source 标注）
        org.mockito.ArgumentCaptor<List<ObjectNode>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(llmClient, org.mockito.Mockito.times(2)).completeChat(captor.capture(), any(ArrayNode.class));
        List<ObjectNode> secondRoundMessages = captor.getAllValues().get(1);
        ObjectNode toolMessage = secondRoundMessages.stream()
                .filter(m -> "tool".equals(m.path("role").asText()))
                .findFirst().orElseThrow();
        assertThat(toolMessage.path("content").asText()).contains("system_data");
        assertThat(toolMessage.path("content").asText()).contains("lamp001");
    }

    // ============ 测试辅助 ============

    private LlmClient.ChatResponse response(String content) {
        return new LlmClient.ChatResponse(content, List.of());
    }

    private LlmClient.ChatResponse responseWithToolCalls(LlmClient.ToolCall... calls) {
        return new LlmClient.ChatResponse(null, List.of(calls));
    }

    private LlmClient.ToolCall toolCall(String id, String name, String argumentsJson) throws Exception {
        return new LlmClient.ToolCall(id, name, objectMapper.readTree(argumentsJson));
    }
}
