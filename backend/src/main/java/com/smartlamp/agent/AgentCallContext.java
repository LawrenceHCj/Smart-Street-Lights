package com.smartlamp.agent;

// 当前 Agent 调用上下文（阶段30）：同一线程内传递 conversationId，
// 供控制工具在生成 Action 时记录"该操作来自哪次会话"。
// 安全约定：conversationId 仅用于会话与 Action 的溯源关联，
// 绝不替代 actionId——用户确认写操作永远按 actionId 精确进行。
public final class AgentCallContext {

    private static final ThreadLocal<String> CONVERSATION_ID = new ThreadLocal<>();

    private AgentCallContext() {
    }

    public static String getConversationId() {
        return CONVERSATION_ID.get();
    }

    public static void setConversationId(String conversationId) {
        CONVERSATION_ID.set(conversationId);
    }

    public static void clear() {
        CONVERSATION_ID.remove();
    }
}
