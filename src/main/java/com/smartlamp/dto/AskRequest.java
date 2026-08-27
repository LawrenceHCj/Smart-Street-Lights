package com.smartlamp.dto;

import lombok.Data;

@Data
public class AskRequest {
    private String question;

    // 可选：会话标识。为空时后端自动创建新会话并在响应中返回 conversationId（Agent V3 多轮记忆）
    private String conversationId;
}