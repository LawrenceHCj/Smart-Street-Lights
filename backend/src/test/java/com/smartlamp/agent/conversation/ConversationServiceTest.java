package com.smartlamp.agent.conversation;

import com.smartlamp.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 阶段25：Conversation / Message 数据模型与基本 Store 接口单测（Mockito，不连 MySQL）
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private AgentConversationRepository conversationRepository;

    @Mock
    private AgentMessageRepository messageRepository;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService();
        ReflectionTestUtils.setField(service, "conversationRepository", conversationRepository);
        ReflectionTestUtils.setField(service, "messageRepository", messageRepository);
    }

    // ============ 创建 Conversation ============

    @Test
    void 创建会话字段完整且conversationId唯一() {
        ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
        when(conversationRepository.save(any(AgentConversation.class))).thenAnswer(i -> i.getArgument(0));

        AgentConversation c1 = service.createConversation("admin", "lamp001 为什么经常离线？");
        AgentConversation c2 = service.createConversation("admin", "lamp001 为什么经常离线？");
        verify(conversationRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        assertThat(c1.getConversationId()).isNotBlank();
        assertThat(c1.getConversationId()).isNotEqualTo(c2.getConversationId()); // 全局唯一
        assertThat(c1.getUserId()).isEqualTo("admin");
        assertThat(c1.getTitle()).isEqualTo("lamp001 为什么经常离线？");
        assertThat(c1.getSummary()).isNull(); // 摘要字段预留，当前为空
        assertThat(c1.getStatus()).isEqualTo("ACTIVE");
        assertThat(c1.getCreatedAt()).isNotNull();
        assertThat(c1.getUpdatedAt()).isNotNull();
        assertThat(c1.getLastMessageAt()).isNotNull();
    }

    @Test
    void 标题超过30字截断() {
        when(conversationRepository.save(any(AgentConversation.class))).thenAnswer(i -> i.getArgument(0));

        AgentConversation c = service.createConversation("admin", "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十多余");

        assertThat(c.getTitle()).hasSizeLessThanOrEqualTo(30);
    }

    @Test
    void 首条问题为空时标题为新会话() {
        when(conversationRepository.save(any(AgentConversation.class))).thenAnswer(i -> i.getArgument(0));

        AgentConversation c = service.createConversation("admin", "");

        assertThat(c.getTitle()).isEqualTo("新会话");
    }

    @Test
    void userId为空拒绝创建() {
        assertThatThrownBy(() -> service.createConversation("  ", "问题"))
                .isInstanceOf(BadRequestException.class);
    }

    // ============ 保存消息 ============

    @Test
    void 保存用户消息字段完整且刷新会话时间() {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId("conv-1");
        conversation.setUserId("admin");
        when(conversationRepository.findByConversationId("conv-1")).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(AgentMessage.class))).thenAnswer(i -> i.getArgument(0));

        AgentMessage message = service.saveUserMessage("conv-1", "lamp001 现在在线吗？");

        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getConversationId()).isEqualTo("conv-1");
        assertThat(message.getRole()).isEqualTo("user");
        assertThat(message.getContent()).isEqualTo("lamp001 现在在线吗？");
        assertThat(message.getCreatedAt()).isNotNull();
        verify(messageRepository).save(any(AgentMessage.class));
        // 会话更新时间被刷新
        assertThat(conversation.getLastMessageAt()).isNotNull();
        verify(conversationRepository).save(conversation);
    }

    @Test
    void 保存助手消息带metadata() {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId("conv-1");
        when(conversationRepository.findByConversationId("conv-1")).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(AgentMessage.class))).thenAnswer(i -> i.getArgument(0));

        AgentMessage message = service.saveAssistantMessage("conv-1", "回答内容", "{\"sources\":[]}");

        assertThat(message.getRole()).isEqualTo("assistant");
        assertThat(message.getMetadata()).isEqualTo("{\"sources\":[]}");
    }

    @Test
    void messageId全局唯一() {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId("conv-1");
        when(conversationRepository.findByConversationId("conv-1")).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(AgentMessage.class))).thenAnswer(i -> i.getArgument(0));

        AgentMessage m1 = service.saveUserMessage("conv-1", "第一条");
        AgentMessage m2 = service.saveUserMessage("conv-1", "第二条");

        assertThat(m1.getMessageId()).isNotEqualTo(m2.getMessageId());
    }

    @Test
    void 消息必须属于明确存在的会话() {
        when(conversationRepository.findByConversationId("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveUserMessage("no-such", "内容"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    void 空消息内容拒绝() {
        assertThatThrownBy(() -> service.saveUserMessage("conv-1", "  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("消息内容");
    }

    // ============ 读取 ============

    @Test
    void 读取会话() {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId("conv-1");
        when(conversationRepository.findByConversationId("conv-1")).thenReturn(Optional.of(conversation));

        assertThat(service.getConversation("conv-1")).contains(conversation);
        assertThat(service.getConversation("conv-2")).isEmpty();
    }

    @Test
    void 会话列表按最近更新倒序查询() {
        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc("admin")).thenReturn(List.of());

        assertThat(service.listConversations("admin")).isEmpty();
        verify(conversationRepository).findByUserIdOrderByUpdatedAtDesc("admin");
    }

    @Test
    void 按时间顺序读取消息() {
        AgentMessage m1 = new AgentMessage();
        m1.setMessageId("m1");
        AgentMessage m2 = new AgentMessage();
        m2.setMessageId("m2");
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc("conv-1"))
                .thenReturn(List.of(m1, m2));

        List<AgentMessage> messages = service.listMessages("conv-1");

        // 读取走"createdAt 升序 + 自增 id 升序兜底"的稳定排序查询
        verify(messageRepository).findByConversationIdOrderByCreatedAtAscIdAsc("conv-1");
        assertThat(messages).containsExactly(m1, m2);
    }

    // ============ 阶段29：删除会话 ============

    @Test
    void 删除会话先删全部消息再删会话() {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId("conv-1");
        when(conversationRepository.findByConversationId("conv-1")).thenReturn(Optional.of(conversation));

        service.deleteConversation("conv-1");

        // 顺序：先删消息（失效其历史），再删会话
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(messageRepository, conversationRepository);
        inOrder.verify(messageRepository).deleteByConversationId("conv-1");
        inOrder.verify(conversationRepository).deleteByConversationId("conv-1");
    }

    @Test
    void 删除不存在的会话报400() {
        when(conversationRepository.findByConversationId("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConversation("no-such"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("会话不存在");
        verify(messageRepository, org.mockito.Mockito.never()).deleteByConversationId(any());
    }

    // ============ 阶段31：持久化设计声明 ============

    @Test
    void 会话与消息为JPA持久化实体服务重启后仍存在() {
        // 设计声明：Conversation/Message 走 MySQL（JPA），服务重启后历史仍在；
        // 与内存态的 AgentAction（重启丢失）形成对照，限制见阶段31 报告。
        assertThat(org.springframework.core.annotation.AnnotatedElementUtils
                .hasAnnotation(AgentConversation.class, jakarta.persistence.Entity.class)).isTrue();
        assertThat(org.springframework.core.annotation.AnnotatedElementUtils
                .hasAnnotation(AgentMessage.class, jakarta.persistence.Entity.class)).isTrue();
    }

    // ============ 阶段修复#8：消息分页与单页上限 ============

    @Test
    void 分页读取按页请求并限制单页上限() {
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(
                org.mockito.ArgumentMatchers.eq("conv-1"),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());

        List<AgentMessage> messages = service.listMessages("conv-1", 0, 9999);

        assertThat(messages).isEmpty();
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(messageRepository).findByConversationIdOrderByCreatedAtAscIdAsc(
                org.mockito.ArgumentMatchers.eq("conv-1"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(200); // 超过上限被截断
    }

    @Test
    void 分页按偏移计算页码() {
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(
                org.mockito.ArgumentMatchers.eq("conv-1"),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());

        service.listMessages("conv-1", 400, 200);

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(messageRepository).findByConversationIdOrderByCreatedAtAscIdAsc(
                org.mockito.ArgumentMatchers.eq("conv-1"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2); // offset 400 / 200
    }
}
