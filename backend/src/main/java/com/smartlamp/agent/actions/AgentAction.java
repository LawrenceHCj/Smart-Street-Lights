package com.smartlamp.agent.actions;

import lombok.Data;

import java.util.Map;

// 统一 Action 数据模型：Agent 一切操作意图的结构化载体。
// 安全约定：
//  - actionType 只能来自 ActionType 白名单（枚举），不存在"任意命令"；
//  - arguments 只能包含该类型白名单允许的键，禁止 command/sql/payload 等万能参数；
//  - 状态不是 CONFIRMED 的 Action 绝不允许进入业务执行（由 ActionGateway 强制）。
@Data
public class AgentAction {

    private String actionId;             // 唯一 ID（UUID）
    private ActionType actionType;       // 操作类型（白名单）
    private String targetType;           // 目标类型（当前仅 device）
    private String targetId;             // 目标标识（如设备编号 lamp001）
    private Map<String, Object> arguments; // 结构化参数（白名单键）
    private ActionRisk riskLevel;        // 风险等级
    private ActionStatus status;         // 生命周期状态
    private long requestedAt;            // 创建时间（epoch ms）
    private long expiresAt;              // 失效时间（epoch ms），过期后不可执行
    private String requestedBy;          // 发起者（用户名，由后端从认证上下文注入，不由 LLM 指定）
    private String conversationId;       // 来源会话（阶段30，仅溯源关联；确认永远只认 actionId，绝不被替代）
    private String originalState;        // 创建时的设备状态快照（如 lampStatus，供审计记录"操作前状态"）
    private String targetState;          // 目标状态（如 ON / OFF，供审计记录"操作后预期状态"）
    private String message;              // 附加说明（拒绝原因 / 执行结果）
}
