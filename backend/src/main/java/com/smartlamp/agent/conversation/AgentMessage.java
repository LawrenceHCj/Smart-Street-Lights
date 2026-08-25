package com.smartlamp.agent.conversation;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

// Agent V3 消息实体（Agent 模块专属表）。
// role 当前支持 user / assistant；如需保存工具调用信息，放入 metadata（JSON 文本）。
// 存储纪律：metadata 只放来源快照等结构化信息，
// 不保存完整 Chain of Thought、模型内部推理、API Key、Token、System Prompt 秘密。
@Data
@Entity
@Table(name = "agent_message")
public class AgentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String messageId;        // 消息唯一标识（UUID）

    @Column(nullable = false)
    private String conversationId;   // 所属会话（弱关联字符串，不建外键）

    @Column(nullable = false)
    private String role;             // user / assistant

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;          // 消息正文

    @Column(columnDefinition = "TEXT")
    private String metadata;         // 扩展信息（JSON 文本，如来源快照；可空）

    private LocalDateTime createdAt;
}
