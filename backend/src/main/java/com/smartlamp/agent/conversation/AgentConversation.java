package com.smartlamp.agent.conversation;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

// Agent V3 会话实体（Agent 模块专属表，与 3号成员业务表分离）。
// 存储纪律：只保存用户可见的对话内容与会话元信息，
// 不保存模型内部推理（Chain of Thought）、API Key、Token、System Prompt 等秘密。
@Data
@Entity
@Table(name = "agent_conversation")
public class AgentConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String conversationId;   // 对外暴露的会话唯一标识（UUID）

    @Column(nullable = false)
    private String userId;           // 会话归属用户（登录用户名）

    private String title;            // 会话标题（首条问题截断 30 字）

    @Column(columnDefinition = "TEXT")
    private String summary;          // 较早历史摘要（触发条件满足时由 LLM 生成）

    // 摘要水位线：id 小于等于该值的消息已被摘要覆盖（消息自增 id 单调，避免重复摘要）
    private Long summarizedUpToId;

    private String status;           // ACTIVE / ARCHIVED

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
}
