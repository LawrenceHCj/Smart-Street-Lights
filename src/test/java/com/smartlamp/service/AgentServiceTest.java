package com.smartlamp.service;

import com.smartlamp.agent.KnowledgeBase;
import com.smartlamp.agent.LlmClient;
import com.smartlamp.agent.LlmException;
import com.smartlamp.agent.PromptProvider;
import com.smartlamp.agent.Retriever;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private LlmClient llmClient;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        Retriever retriever = new Retriever();
        ReflectionTestUtils.setField(retriever, "knowledgeBase", new KnowledgeBase());

        agentService = new AgentService();
        ReflectionTestUtils.setField(agentService, "retriever", retriever);
        ReflectionTestUtils.setField(agentService, "promptProvider", new PromptProvider());
        ReflectionTestUtils.setField(agentService, "llmClient", llmClient);
    }

    @Test
    void 离线问题返回知识库回答() {
        // 未配置大模型（mock 默认 false），走本地知识库回答
        AskResponse result = agentService.ask("路灯离线应该怎么排查？");

        assertThat(result.getAnswer()).contains("心跳");
        assertThat(result.getSources()).hasSize(2);
        assertThat(result.getSources().get(0).getTitle()).isEqualTo("设备离线排查");
        assertThat(result.getSources().get(0).getSection()).isEqualTo("告警处理");
        assertThat(result.getSources().get(0).getScore()).isEqualTo(1.0);
    }

    @Test
    void 阈值问题返回光照联动控制知识() {
        AskResponse result = agentService.ask("光照阈值怎么设置？");

        assertThat(result.getSources().get(0).getTitle()).isEqualTo("光照联动控制");
    }

    @Test
    void 手动控制问题返回控制回执知识() {
        AskResponse result = agentService.ask("手动控制没有回执怎么办？");

        assertThat(result.getSources().get(0).getTitle()).isEqualTo("手动控制回执");
    }

    @Test
    void 无命中返回提示和空来源() {
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

    @Test
    void 配置大模型时由模型生成回答() {
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.completeChat(anyString(), anyString()))
                .thenReturn("模拟大模型回答：请先检查供电与网关连接。");

        AskResponse result = agentService.ask("路灯离线应该怎么排查？");

        assertThat(result.getAnswer()).isEqualTo("模拟大模型回答：请先检查供电与网关连接。");
        assertThat(result.getSources().get(0).getTitle()).isEqualTo("设备离线排查");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeChat(systemCaptor.capture(), userCaptor.capture());
        assertThat(systemCaptor.getValue()).contains("智慧路灯维护助手");
        assertThat(userCaptor.getValue()).contains("《设备离线排查》");
        assertThat(userCaptor.getValue()).contains("告警处理");
    }

    @Test
    void 无命中但配置大模型时由模型按范围回答() {
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.completeChat(anyString(), anyString())).thenReturn("模拟大模型回答：建议每季度巡检一次。");

        AskResponse result = agentService.ask("路灯一般多久需要维护一次？");

        assertThat(result.getAnswer()).contains("每季度");
        assertThat(result.getSources()).isEmpty();
    }

    @Test
    void 模型调用失败时降级为本地知识库回答() {
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.completeChat(anyString(), anyString()))
                .thenThrow(new LlmException("LLM API 返回 500"));

        AskResponse result = agentService.ask("路灯离线应该怎么排查？");

        assertThat(result.getAnswer()).contains("心跳");
        assertThat(result.getSources().get(0).getTitle()).isEqualTo("设备离线排查");
    }
}
