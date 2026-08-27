package com.smartlamp.agent.conversation;

import com.smartlamp.agent.LlmClient;
import com.smartlamp.agent.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 阶段28：长对话摘要器单测（Mockito，不连 MySQL）
@ExtendWith(MockitoExtension.class)
class ConversationSummarizerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private LlmClient llmClient;

    private ConversationSummarizer summarizer;

    @BeforeEach
    void setUp() {
        summarizer = new ConversationSummarizer();
        ReflectionTestUtils.setField(summarizer, "conversationService", conversationService);
        ReflectionTestUtils.setField(summarizer, "llmClient", llmClient);
    }

    private AgentConversation conversation(String summary, Long summarizedUpToId) {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId("conv-1");
        conversation.setUserId("admin");
        conversation.setSummary(summary);
        conversation.setSummarizedUpToId(summarizedUpToId);
        return conversation;
    }

    // 生成 n 条消息（id 1..n，user/assistant 交替）
    private List<AgentMessage> messages(int n) {
        List<AgentMessage> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            AgentMessage message = new AgentMessage();
            message.setId((long) i);
            message.setMessageId("m" + i);
            message.setRole(i % 2 == 1 ? "user" : "assistant");
            message.setContent("第 " + i + " 条消息，讨论 lamp001 状态");
            list.add(message);
        }
        return list;
    }

    // ============ 触发机制 ============

    @Test
    void 未配置大模型时跳过摘要() {
        when(llmClient.isConfigured()).thenReturn(false);

        summarizer.summarizeIfNeeded("conv-1");

        verify(llmClient, never()).completeChat(anyString(), anyString());
    }

    @Test
    void 短对话不摘要() {
        // 15 条消息：窗口外 = 9 条 < 阈值 10，不触发
        AgentConversation conversation = conversation(null, 0L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation));
        when(conversationService.listMessages("conv-1")).thenReturn(messages(15));

        summarizer.summarizeIfNeeded("conv-1");

        verify(llmClient, never()).completeChat(anyString(), anyString());
        verify(conversationService, never()).saveConversation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 长对话触发摘要并更新摘要与水位线() {
        // 20 条消息：窗口外 = 14 条未摘要 >= 阈值 10，触发
        AgentConversation conversation = conversation(null, 0L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation));
        when(conversationService.listMessages("conv-1")).thenReturn(messages(20));
        when(llmClient.completeChat(anyString(), anyString())).thenReturn("用户一直在讨论 lamp001 的离线问题");

        summarizer.summarizeIfNeeded("conv-1");

        verify(llmClient, times(1)).completeChat(anyString(), anyString());
        // 摘要内容与水位线更新（窗口 6 条，窗口外最后一条 id = 14）
        assertThat(conversation.getSummary()).isEqualTo("用户一直在讨论 lamp001 的离线问题");
        assertThat(conversation.getSummarizedUpToId()).isEqualTo(14L);
        verify(conversationService).saveConversation(conversation);
    }

    @Test
    void 水位线生效不重复摘要() {
        // 已摘要到 id=14；新增至 25 条：窗口外 = 19 条，未摘要 = id 15..19 共 5 条 < 10，不触发
        AgentConversation conversation = conversation("旧摘要", 14L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation));
        when(conversationService.listMessages("conv-1")).thenReturn(messages(25));

        summarizer.summarizeIfNeeded("conv-1");

        verify(llmClient, never()).completeChat(anyString(), anyString());
    }

    @Test
    void 摘要输入包含旧摘要与待摘要消息() {
        AgentConversation conversation = conversation("旧摘要：讨论过 lamp001", 0L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation));
        when(conversationService.listMessages("conv-1")).thenReturn(messages(20));
        when(llmClient.completeChat(anyString(), anyString())).thenReturn("新摘要");

        summarizer.summarizeIfNeeded("conv-1");

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeChat(anyString(), userCaptor.capture());
        assertThat(userCaptor.getValue()).contains("旧摘要：讨论过 lamp001");
        assertThat(userCaptor.getValue()).contains("user: 第 1 条消息");
        assertThat(userCaptor.getValue()).contains("assistant: 第 2 条消息");
    }

    // ============ 失败静默 ============

    @Test
    void 摘要调用失败静默不影响主流程() {
        AgentConversation conversation = conversation(null, 0L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation));
        when(conversationService.listMessages("conv-1")).thenReturn(messages(20));
        when(llmClient.completeChat(anyString(), anyString())).thenThrow(new LlmException("LLM API 返回 500"));

        summarizer.summarizeIfNeeded("conv-1"); // 不抛异常

        assertThat(conversation.getSummary()).isNull();
        verify(conversationService, never()).saveConversation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 摘要结果为空跳过更新() {
        AgentConversation conversation = conversation(null, 0L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(conversationService.getConversation("conv-1")).thenReturn(Optional.of(conversation));
        when(conversationService.listMessages("conv-1")).thenReturn(messages(20));
        when(llmClient.completeChat(anyString(), anyString())).thenReturn("  ");

        summarizer.summarizeIfNeeded("conv-1");

        assertThat(conversation.getSummary()).isNull();
    }

    // ============ 摘要 Prompt 安全规则 ============

    @Test
    void 摘要Prompt要求实时状态带时间语义() {
        assertThat(ConversationSummarizer.SUMMARY_PROMPT).contains("带时间语义");
        assertThat(ConversationSummarizer.SUMMARY_PROMPT).contains("在之前的对话中");
        assertThat(ConversationSummarizer.SUMMARY_PROMPT).contains("绝不能写成当前事实");
    }

    @Test
    void 摘要Prompt禁止秘密与内部推理() {
        assertThat(ConversationSummarizer.SUMMARY_PROMPT).contains("不得包含 API Key");
        assertThat(ConversationSummarizer.SUMMARY_PROMPT).contains("模型内部推理");
    }
}
