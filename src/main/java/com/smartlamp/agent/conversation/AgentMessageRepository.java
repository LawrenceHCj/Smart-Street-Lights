package com.smartlamp.agent.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 消息仓储（复用项目现有 Spring Data JPA 模式）
public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    // 按时间升序读取会话消息；同毫秒时按自增 id 升序兜底，保证保存顺序稳定
    List<AgentMessage> findByConversationIdOrderByCreatedAtAscIdAsc(String conversationId);

    long countByConversationId(String conversationId);

    // 删除会话的全部消息（随会话删除，由 ConversationService 的事务方法调用）
    void deleteByConversationId(String conversationId);
}
