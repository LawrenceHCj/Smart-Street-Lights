package com.smartlamp.agent.conversation;

import com.smartlamp.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Agent V3 会话服务：会话与消息的基本存储接口。
// 本阶段只提供数据层能力（创建/保存/读取），不修改 Agent Prompt 与 LLM 上下文逻辑。
// 保存纪律：只保存 user/assistant 消息正文与可选 metadata（JSON），
// 不保存模型内部推理、API Key、Token、System Prompt。
@Service
public class ConversationService {

    private static final int TITLE_MAX_LENGTH = 30;

    @Autowired
    private AgentConversationRepository conversationRepository;

    @Autowired
    private AgentMessageRepository messageRepository;

    // 创建会话：userId 为登录用户名；firstQuestion 为空时标题用"新会话"
    public AgentConversation createConversation(String userId, String firstQuestion) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("userId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setUserId(userId.trim());
        conversation.setTitle(buildTitle(firstQuestion));
        conversation.setSummary(null);
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversation.setLastMessageAt(now);
        return conversationRepository.save(conversation);
    }

    // 按 conversationId 查询会话
    public Optional<AgentConversation> getConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new BadRequestException("conversationId 不能为空");
        }
        return conversationRepository.findByConversationId(conversationId.trim());
    }

    // 当前用户的会话列表（按最近更新倒序）
    public List<AgentConversation> listConversations(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("userId 不能为空");
        }
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId.trim());
    }

    // 保存会话（摘要更新等场景复用）
    public AgentConversation saveConversation(AgentConversation conversation) {
        if (conversation == null || conversation.getConversationId() == null) {
            throw new BadRequestException("会话不能为空");
        }
        return conversationRepository.save(conversation);
    }

    // 保存用户消息（会校验会话存在，并刷新会话更新时间）
    public AgentMessage saveUserMessage(String conversationId, String content) {
        return saveMessage(conversationId, "user", content, null);
    }

    // 保存助手消息（metadata 为 JSON 文本，如回答来源快照；可空）
    public AgentMessage saveAssistantMessage(String conversationId, String content, String metadataJson) {
        return saveMessage(conversationId, "assistant", content, metadataJson);
    }

    // 按时间顺序读取会话消息（createdAt 升序，同毫秒按自增 id 升序兜底）
    public List<AgentMessage> listMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new BadRequestException("conversationId 不能为空");
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId.trim());
    }

    // 删除会话及其全部历史消息（同一事务：要么全删，要么全不删）
    @Transactional
    public void deleteConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new BadRequestException("conversationId 不能为空");
        }
        String id = conversationId.trim();
        AgentConversation conversation = conversationRepository.findByConversationId(id)
                .orElseThrow(() -> new BadRequestException("会话不存在: " + id));
        messageRepository.deleteByConversationId(id);      // 先删消息（失效其历史）
        conversationRepository.deleteByConversationId(id); // 再删会话
    }

    // ============ 私有方法 ============

    private AgentMessage saveMessage(String conversationId, String role, String content, String metadataJson) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new BadRequestException("conversationId 不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BadRequestException("消息内容不能为空");
        }
        // Message 必须属于明确存在的 Conversation
        AgentConversation conversation = conversationRepository.findByConversationId(conversationId.trim())
                .orElseThrow(() -> new BadRequestException("会话不存在: " + conversationId));

        AgentMessage message = new AgentMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setConversationId(conversation.getConversationId());
        message.setRole(role);
        message.setContent(content.trim());
        message.setMetadata(metadataJson);
        message.setCreatedAt(LocalDateTime.now());
        AgentMessage saved = messageRepository.save(message);

        // 刷新会话更新时间（保存顺序按消息 createdAt，稳定）
        conversation.setUpdatedAt(saved.getCreatedAt());
        conversation.setLastMessageAt(saved.getCreatedAt());
        conversationRepository.save(conversation);
        return saved;
    }

    // 标题：首条问题去掉换行后截断 30 字；为空时用"新会话"
    private String buildTitle(String firstQuestion) {
        if (firstQuestion == null || firstQuestion.isBlank()) {
            return "新会话";
        }
        String text = firstQuestion.trim().replaceAll("\\s+", " ");
        return text.length() <= TITLE_MAX_LENGTH ? text : text.substring(0, TITLE_MAX_LENGTH);
    }
}
