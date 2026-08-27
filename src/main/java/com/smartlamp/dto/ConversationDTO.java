package com.smartlamp.dto;

import com.smartlamp.agent.conversation.AgentConversation;
import lombok.Data;

import java.time.LocalDateTime;

// 会话列表/详情对外 DTO：只暴露前端需要的字段，不暴露 userId 等内部信息
@Data
public class ConversationDTO {

    private String conversationId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;

    public ConversationDTO() {
    }

    public ConversationDTO(String conversationId, String title, LocalDateTime createdAt,
                           LocalDateTime updatedAt, LocalDateTime lastMessageAt) {
        this.conversationId = conversationId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastMessageAt = lastMessageAt;
    }

    // 从实体映射（只取对外字段）
    public static ConversationDTO from(AgentConversation conversation) {
        return new ConversationDTO(
                conversation.getConversationId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getLastMessageAt());
    }
}
