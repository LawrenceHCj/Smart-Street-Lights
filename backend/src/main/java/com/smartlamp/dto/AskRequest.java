package com.smartlamp.dto;

import lombok.Data;

@Data
public class AskRequest {
    private String question;

    // 可选：会话标识。为空时后端自动创建新会话并在响应中返回 conversationId（Agent V3 多轮记忆）
    private String conversationId;

    // 可选：请求幂等标识（阶段修复#10）。前端超时重试时携带同一 requestId，
    // 后端返回首次结果且不重复保存消息、不重复执行工具
    private String requestId;
}
