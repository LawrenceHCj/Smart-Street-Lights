package com.smartlamp.service;

import com.smartlamp.agent.AgentCallContext;
import com.smartlamp.agent.actions.ActionService;
import com.smartlamp.agent.conversation.AgentConversation;
import com.smartlamp.agent.conversation.AgentMessage;
import com.smartlamp.agent.conversation.ConversationService;
import com.smartlamp.agent.conversation.ConversationSummarizer;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.SourceItem;
import com.smartlamp.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 阶段26：会话生命周期编排单测（Mockito，不连 MySQL）
@ExtendWith(MockitoExtension.class)
class AgentConversationServiceTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private AgentService agentService;

    @Mock
    private ConversationSummarizer conversationSummarizer;

    @Mock
    private ActionService actionService;

    private AgentConversationService service;

    @BeforeEach
    void setUp() {
        service = new AgentConversationService();
        ReflectionTestUtils.setField(service, "conversationService", conversationService);
        ReflectionTestUtils.setField(service, "agentService", agentService);
        ReflectionTestUtils.setField(service, "conversationSummarizer", conversationSummarizer);
        ReflectionTestUtils.setField(service, "actionService", actionService);
    }

    private AgentConversation conversation(String conversationId, String userId) {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId(conversationId);
        conversation.setUserId(userId);
        return conversation;
    }

    // ============ 场景1：第一次聊天自动建立 Conversation ============

    @Test
    void 第一次聊天无conversationId自动建立会话并完整落库() {
        AgentConversation created = conversation("conv-new", "admin");
        when(conversationService.createConversation("admin", "lamp001 现在在线吗？")).thenReturn(created);
        when(conversationService.listMessages("conv-new")).thenReturn(List.of());
        when(agentService.ask(eq("lamp001 现在在线吗？"), anyList(), any()))
                .thenReturn(new AskResponse("在线", List.of(new SourceItem("设备状态", "system_data", 1.0))));

        AskResponse response = service.chat("lamp001 现在在线吗？", null, "admin");

        // 保存 User Message + 调用 Agent（带历史与摘要参数）+ 保存 Assistant Message（metadata 为来源快照 JSON）
        verify(conversationService).saveUserMessage("conv-new", "lamp001 现在在线吗？");
        verify(agentService).ask(eq("lamp001 现在在线吗？"), anyList(), any());
        verify(conversationService).saveAssistantMessage(eq("conv-new"), eq("在线"), anyString());
        // 返回新 conversationId + answer
        assertThat(response.getConversationId()).isEqualTo("conv-new");
        assertThat(response.getAnswer()).isEqualTo("在线");
        assertThat(response.getSources()).hasSize(1);
    }

    @Test
    void 助手消息metadata保存来源快照() {
        AgentConversation created = conversation("conv-new", "admin");
        when(conversationService.createConversation(anyString(), anyString())).thenReturn(created);
        when(conversationService.listMessages("conv-new")).thenReturn(List.of());
        when(agentService.ask(anyString(), anyList(), any()))
                .thenReturn(new AskResponse("回答", List.of(new SourceItem("设备离线排查", "knowledge", 2.0))));

        service.chat("路灯离线怎么排查？", null, "admin");

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(conversationService).saveAssistantMessage(eq("conv-new"), anyString(), captor.capture());
        assertThat(captor.getValue()).contains("设备离线排查").contains("knowledge");
    }

    // ============ 场景2：第二次使用相同 conversationId ============

    @Test
    void 第二次聊天使用相同conversationId不新建会话() {
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation("conv-1", "admin")));
        when(conversationService.listMessages("conv-1")).thenReturn(List.of());
        when(agentService.ask(anyString(), anyList(), any())).thenReturn(new AskResponse("在线", List.of()));

        AskResponse response = service.chat("它现在在线吗？", "conv-1", "admin");

        verify(conversationService, never()).createConversation(any(), any());
        verify(conversationService).saveUserMessage("conv-1", "它现在在线吗？");
        assertThat(response.getConversationId()).isEqualTo("conv-1");
    }

    // ============ 场景3：读取完整历史 ============

    @Test
    void 读取会话完整历史按时间顺序() {
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation("conv-1", "admin")));
        AgentMessage m1 = new AgentMessage();
        m1.setMessageId("m1");
        AgentMessage m2 = new AgentMessage();
        m2.setMessageId("m2");
        when(conversationService.listMessages("conv-1")).thenReturn(List.of(m1, m2));

        List<AgentMessage> messages = service.getMessages("conv-1", "admin");

        assertThat(messages).containsExactly(m1, m2);
        verify(conversationService).listMessages("conv-1");
    }

    // ============ 场景4：不存在的 conversationId ============

    @Test
    void 不存在的conversationId聊天报错() {
        when(conversationService.getConversation("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.chat("问题", "no-such", "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
        verify(conversationService, never()).saveUserMessage(any(), any());
        verify(agentService, never()).ask(any(), anyList(), any());
    }

    @Test
    void 不存在的conversationId读取历史报错() {
        when(conversationService.getConversation("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages("no-such", "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
    }

    // ============ 场景5：空消息 ============

    @Test
    void 空消息聊天报400() {
        assertThatThrownBy(() -> service.chat("", null, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("question");
        assertThatThrownBy(() -> service.chat("  ", "conv-1", "admin"))
                .isInstanceOf(BadRequestException.class);
    }

    // ============ 阶段27：最近历史注入 ============

    private AgentMessage msg(String messageId, String role, String content) {
        AgentMessage message = new AgentMessage();
        message.setMessageId(messageId);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    @Test
    void 聊天时把最近历史传给Agent且排除当前用户消息() {
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation("conv-1", "admin")));
        // 历史：assistant(回答) + user(当前消息，最后一条)
        AgentMessage historyAnswer = msg("m1", "assistant", "lamp001 在线");
        AgentMessage currentUser = msg("m2", "user", "它最近有告警吗？");
        when(conversationService.listMessages("conv-1")).thenReturn(List.of(historyAnswer, currentUser));
        when(agentService.ask(anyString(), anyList(), any())).thenReturn(new AskResponse("回答", List.of()));

        service.chat("它最近有告警吗？", "conv-1", "admin");

        org.mockito.ArgumentCaptor<List<AgentMessage>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(agentService).ask(eq("它最近有告警吗？"), captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(historyAnswer); // 排除当前用户消息
    }

    @Test
    void 历史超过上限只取最近6条() {
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation("conv-1", "admin")));
        // 共 10 条：m0..m8 为历史，m9 为当前用户消息
        java.util.ArrayList<AgentMessage> all = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            all.add(msg("m" + i, i % 2 == 0 ? "user" : "assistant", "历史消息 " + i));
        }
        all.add(msg("m9", "user", "当前问题"));
        when(conversationService.listMessages("conv-1")).thenReturn(all);
        when(agentService.ask(anyString(), anyList(), any())).thenReturn(new AskResponse("回答", List.of()));

        service.chat("当前问题", "conv-1", "admin");

        org.mockito.ArgumentCaptor<List<AgentMessage>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(agentService).ask(eq("当前问题"), captor.capture(), any());
        // 最近 6 条历史 = m3..m8
        assertThat(captor.getValue()).hasSize(6);
        assertThat(captor.getValue().get(0).getMessageId()).isEqualTo("m3");
        assertThat(captor.getValue().get(5).getMessageId()).isEqualTo("m8");
    }

    @Test
    void 首次聊天无历史时传空列表() {
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation("conv-1", "admin")));
        when(conversationService.listMessages("conv-1")).thenReturn(List.of(msg("m1", "user", "当前问题")));
        when(agentService.ask(anyString(), anyList(), any())).thenReturn(new AskResponse("回答", List.of()));

        service.chat("当前问题", "conv-1", "admin");

        org.mockito.ArgumentCaptor<List<AgentMessage>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(agentService).ask(eq("当前问题"), captor.capture(), any());
        assertThat(captor.getValue()).isEmpty();
    }

    // ============ 阶段28：摘要注入与触发 ============

    @Test
    void 聊天时把会话摘要传给Agent并在结束后触发摘要检查() {
        AgentConversation conversation = conversation("conv-1", "admin");
        conversation.setSummary("用户此前讨论过 lamp001 的离线问题");
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation));
        when(conversationService.listMessages("conv-1")).thenReturn(List.of(msg("m1", "user", "继续分析")));
        when(agentService.ask(anyString(), anyList(), any())).thenReturn(new AskResponse("回答", List.of()));

        service.chat("继续分析", "conv-1", "admin");

        // 摘要传给 Agent
        org.mockito.ArgumentCaptor<String> summaryCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(agentService).ask(eq("继续分析"), anyList(), summaryCaptor.capture());
        assertThat(summaryCaptor.getValue()).isEqualTo("用户此前讨论过 lamp001 的离线问题");
        // 消息保存后触发摘要检查（内部有阈值控制）
        verify(conversationSummarizer).summarizeIfNeeded("conv-1");
    }

    // ============ 会话归属 ============

    @Test
    void 访问他人会话按不存在处理() {
        when(conversationService.getConversation("conv-other"))
                .thenReturn(Optional.of(conversation("conv-other", "other-user")));

        assertThatThrownBy(() -> service.chat("问题", "conv-other", "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
        assertThatThrownBy(() -> service.getMessages("conv-other", "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
    }

    // ============ 阶段29：会话列表 / 详情 / 删除 ============

    @Test
    void 会话列表按用户查询并映射对外字段() {
        AgentConversation conversation = conversation("conv-1", "admin");
        conversation.setTitle("lamp001 离线问题");
        when(conversationService.listConversations("admin")).thenReturn(List.of(conversation));

        List<com.smartlamp.dto.ConversationDTO> list = service.listConversations("admin");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getConversationId()).isEqualTo("conv-1");
        assertThat(list.get(0).getTitle()).isEqualTo("lamp001 离线问题");
        verify(conversationService).listConversations("admin");
    }

    @Test
    void 读取会话详情校验归属() {
        when(conversationService.getConversation("conv-1"))
                .thenReturn(Optional.of(conversation("conv-1", "admin")));

        com.smartlamp.dto.ConversationDTO detail = service.getConversationDetail("conv-1", "admin");

        assertThat(detail.getConversationId()).isEqualTo("conv-1");
    }

    @Test
    void 读取他人会话详情按不存在处理() {
        when(conversationService.getConversation("conv-other"))
                .thenReturn(Optional.of(conversation("conv-other", "other-user")));

        assertThatThrownBy(() -> service.getConversationDetail("conv-other", "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    void 删除会话校验归属并委托删除() {
        when(conversationService.getConversation("conv-1"))
                .thenReturn(Optional.of(conversation("conv-1", "admin")));

        service.deleteConversation("conv-1", "admin");

        verify(conversationService).deleteConversation("conv-1");
    }

    @Test
    void 删除他人会话被拒绝且不删除() {
        when(conversationService.getConversation("conv-other"))
                .thenReturn(Optional.of(conversation("conv-other", "other-user")));

        assertThatThrownBy(() -> service.deleteConversation("conv-other", "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
        verify(conversationService, never()).deleteConversation(any());
    }

    // ============ 会话与 Action 的安全关系（阶段30） ============

    @Test
    void 删除会话时先取消其待确认Action() {
        when(conversationService.getConversation("conv-1"))
                .thenReturn(Optional.of(conversation("conv-1", "admin")));

        service.deleteConversation("conv-1", "admin");

        verify(actionService).cancelPendingByConversation("conv-1");
        verify(conversationService).deleteConversation("conv-1");
    }

    @Test
    void 删除他人会话不触发Action取消() {
        when(conversationService.getConversation("conv-other"))
                .thenReturn(Optional.of(conversation("conv-other", "other-user")));

        assertThatThrownBy(() -> service.deleteConversation("conv-other", "admin"))
                .isInstanceOf(BadRequestException.class);
        verify(actionService, never()).cancelPendingByConversation(anyString());
    }

    @Test
    void 聊天结束后清理会话上下文() {
        AgentConversation created = conversation("conv-new", "admin");
        when(conversationService.createConversation(anyString(), anyString())).thenReturn(created);
        when(conversationService.listMessages("conv-new")).thenReturn(List.of());
        when(agentService.ask(anyString(), anyList(), any()))
                .thenReturn(new AskResponse("回答", List.of()));

        service.chat("你好", null, "admin");

        assertThat(AgentCallContext.getConversationId()).isNull();
    }

    // ============ 阶段31：历史对话完整测试补充 ============

    @Test
    void 删除会话后无法再获取旧消息() {
        when(conversationService.getConversation("conv-1"))
                .thenReturn(Optional.of(conversation("conv-1", "admin")))
                .thenReturn(Optional.empty()); // 删除后会话不存在

        service.deleteConversation("conv-1", "admin");

        assertThatThrownBy(() -> service.getMessages("conv-1", "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    void 助手消息metadata仅含来源快照不含密钥类信息() {
        AgentConversation created = conversation("conv-new", "admin");
        when(conversationService.createConversation(anyString(), anyString())).thenReturn(created);
        when(conversationService.listMessages("conv-new")).thenReturn(List.of());
        when(agentService.ask(anyString(), anyList(), any()))
                .thenReturn(new AskResponse("回答", List.of(new SourceItem("设备离线排查", "knowledge", 2.0))));

        service.chat("路灯离线怎么排查？", null, "admin");

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(conversationService).saveAssistantMessage(eq("conv-new"), anyString(), captor.capture());
        assertThat(captor.getValue())
                .contains("设备离线排查") // 只有来源快照
                .doesNotContain("apiKey").doesNotContain("token").doesNotContain("Authorization");
    }

    // ============ 阶段修复#10：聊天请求幂等 ============

    @Test
    void 同requestId重试返回首次结果且只保存一次消息() {
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation("conv-1", "admin")));
        when(conversationService.listMessages("conv-1")).thenReturn(List.of());
        when(agentService.ask(anyString(), anyList(), any()))
                .thenReturn(new AskResponse("回答", List.of()));

        AskResponse first = service.chat("问题", "conv-1", "admin", "req-1");
        AskResponse second = service.chat("问题", "conv-1", "admin", "req-1");

        assertThat(second.getAnswer()).isEqualTo("回答");
        assertThat(second.getConversationId()).isEqualTo(first.getConversationId());
        // 只保存一次用户消息与助手消息，LLM 只执行一次
        verify(conversationService).saveUserMessage("conv-1", "问题");
        verify(conversationService).saveAssistantMessage(eq("conv-1"), anyString(), anyString());
        verify(agentService).ask(anyString(), anyList(), any());
    }

    @Test
    void requestId为空时不启用幂等每次正常处理() {
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation("conv-1", "admin")));
        when(conversationService.listMessages("conv-1")).thenReturn(List.of());
        when(agentService.ask(anyString(), anyList(), any())).thenReturn(new AskResponse("回答", List.of()));

        service.chat("问题", "conv-1", "admin");
        service.chat("问题", "conv-1", "admin");

        verify(agentService, org.mockito.Mockito.times(2)).ask(anyString(), anyList(), any());
    }

    // ============ 阶段修复#8：会话历史分页 ============

    @Test
    void 分页读取历史委托ConversationService() {
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation("conv-1", "admin")));
        when(conversationService.listMessages("conv-1", 200, 100)).thenReturn(List.of());

        List<AgentMessage> messages = service.getMessages("conv-1", "admin", 200, 100);

        assertThat(messages).isEmpty();
        verify(conversationService).listMessages("conv-1", 200, 100);
    }
}
