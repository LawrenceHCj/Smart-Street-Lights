package com.smartlamp.agent.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 会话仓储（复用项目现有 Spring Data JPA 模式，与 3号成员 Repository 同风格）
public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {

    Optional<AgentConversation> findByConversationId(String conversationId);

    // 当前用户会话列表，按最近更新倒序（用于前端会话列表）
    List<AgentConversation> findByUserIdOrderByUpdatedAtDesc(String userId);

    boolean existsByConversationId(String conversationId);

    // 删除会话（随会话删除，由 ConversationService 的事务方法调用）
    void deleteByConversationId(String conversationId);
}
