package com.smartlamp.agent.actions;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

// Agent 写操作审计实体（阶段19，Agent 模块专属表，与 agent_message 同先例：复用同一 MySQL/JPA 体系）。
// 每条记录对应一个 Action（按 actionId 唯一，随状态流转 upsert 更新），
// 失败、取消、过期、超时同样落库，保证"审计日志与 Action 状态一致"。
// 存储纪律：只保存结构化字段与 arguments（白名单键校验后的 JSON），
// 不保存 API Key、Token、System Prompt 等秘密。
@Data
@Entity
@Table(name = "agent_action_audit", indexes = {
        @Index(name = "idx_audit_action_id", columnList = "actionId", unique = true),
        @Index(name = "idx_audit_requested_by", columnList = "requestedBy, requestedAt")
})
public class AgentActionAudit {

    public static final String SOURCE_AI_AGENT = "AI_AGENT"; // 审计来源：当前全部 Action 均由智能体产生

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String actionId;             // Action 唯一标识

    @Column(nullable = false)
    private String source;               // 来源，恒为 AI_AGENT

    private String requestedBy;          // 发起者（认证上下文注入）

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;       // 操作类型（白名单）

    private String targetType;           // 目标类型
    private String targetId;             // 目标标识（如设备编号）

    @Column(length = 2000)
    private String arguments;            // 结构化参数 JSON（白名单键，不含秘密）

    private String originalState;        // 操作前状态快照（如创建时的 lampStatus）
    private String targetState;          // 目标状态（如 ON / OFF）

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionStatus status;         // 当前/最终状态（与 Action 状态一致）

    private Long requestedAt;            // 创建时间（epoch ms）
    private Long confirmedAt;            // 确认时间（epoch ms，可空）
    private Long executedAt;             // 开始执行时间（epoch ms，可空）

    @Column(length = 1000)
    private String result;               // 结果描述（成功/已接受/已取消等）

    @Column(length = 1000)
    private String error;                // 错误描述（失败/过期/拒绝等）

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
