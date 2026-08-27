package com.smartlamp.service;

import com.smartlamp.agent.AgentCallContext;
import com.smartlamp.agent.actions.ActionService;
import com.smartlamp.agent.conversation.AgentConversation;
import com.smartlamp.agent.conversation.AgentMessage;
import com.smartlamp.agent.conversation.ConversationService;
import com.smartlamp.agent.conversation.ConversationSummarizer;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.ConversationDTO;
import com.smartlamp.dto.SourceItem;
import com.smartlamp.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

// Agent V3 聊天会话编排：完整聊天流程的唯一入口。
// 收到用户消息 → 确认 Conversation 存在（无 conversationId 自动新建）→ 保存 User Message
// → 调用现有 AgentService 生成回答（Summary + 最近历史 + 当前问题）→ 保存 Assistant Message
// → 返回 conversationId + answer + sources；之后按需触发长对话摘要（不阻塞主流程）。
// 会话按 userId 隔离：访问他人会话统一按"会话不存在"处理，不暴露存在性。
@Service
public class AgentConversationService {

    // 注入给 LLM 的最近历史条数（约 3 轮问答，兼顾指代消解与上下文开销，不无限塞历史）
    public static final int RECENT_HISTORY_LIMIT = 6;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private ConversationSummarizer conversationSummarizer;

    @Autowired
    private ActionService actionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 完整聊天流程（向后兼容：conversationId 为空时自动创建新会话）
    public AskResponse chat(String question, String conversationId, String userId) {
        if (question == null || question.isBlank()) {
            throw new BadRequestException("question 不能为空");
        }
        String text = question.trim();

        // 1. 确认 Conversation 存在（无 conversationId 自动新建，标题取首条问题）
        AgentConversation conversation;
        if (conversationId == null || conversationId.isBlank()) {
            conversation = conversationService.createConversation(userId, text);
        } else {
            conversation = requireOwnConversation(conversationId, userId);
        }

        // 2. 保存 User Message
        conversationService.saveUserMessage(conversation.getConversationId(), text);

        // 3. 取最近历史（排除刚保存的当前用户消息）→ 调用现有 Agent（Summary + 历史 + 当前问题）
        // 注入 conversationId 上下文：控制工具生成 Action 时记录来源会话（仅溯源，绝不替代 actionId 确认）
        List<AgentMessage> recentHistory = recentHistory(conversation.getConversationId());
        AgentCallContext.setConversationId(conversation.getConversationId());
        AskResponse response;
        try {
            response = agentService.ask(text, recentHistory, conversation.getSummary());
        } finally {
            AgentCallContext.clear();
        }

        // 4. 保存 Assistant Message（metadata 存回答来源快照）
        conversationService.saveAssistantMessage(conversation.getConversationId(),
                response.getAnswer(), serializeSources(response.getSources()));

        // 5. 按需触发长对话摘要（内部有阈值控制且失败静默，不阻塞本次响应）
        conversationSummarizer.summarizeIfNeeded(conversation.getConversationId());

        // 6. 返回 conversationId + answer + sources（action 等来源原样保留）
        response.setConversationId(conversation.getConversationId());
        return response;
    }

    // 最近历史：按时间升序的最近 RECENT_HISTORY_LIMIT 条，且不含刚保存的当前用户消息
    private List<AgentMessage> recentHistory(String conversationId) {
        List<AgentMessage> all = conversationService.listMessages(conversationId);
        int end = Math.max(0, all.size() - 1); // 最后一条是当前用户消息，排除
        int start = Math.max(0, end - RECENT_HISTORY_LIMIT);
        return all.subList(start, end);
    }

    // 创建新会话（POST /api/assistant/conversations）
    public AgentConversation createConversation(String userId, String firstQuestion) {
        return conversationService.createConversation(userId, firstQuestion);
    }

    // 读取某个 Conversation 的完整历史（按时间升序）
    public List<AgentMessage> getMessages(String conversationId, String userId) {
        requireOwnConversation(conversationId, userId);
        return conversationService.listMessages(conversationId);
    }

    // 当前用户的会话列表（按最近更新倒序，只返回对外字段）
    public List<ConversationDTO> listConversations(String userId) {
        return conversationService.listConversations(userId).stream()
                .map(ConversationDTO::from)
                .toList();
    }

    // 读取单个会话详情（校验归属）
    public ConversationDTO getConversationDetail(String conversationId, String userId) {
        return ConversationDTO.from(requireOwnConversation(conversationId, userId));
    }

    // 删除会话及其全部历史消息（校验归属；他人会话按"会话不存在"处理）
    public void deleteConversation(String conversationId, String userId) {
        requireOwnConversation(conversationId, userId);
        // 安全处理（阶段30）：先取消该会话尚未确认的 Action，避免悬空的待确认操作
        actionService.cancelPendingByConversation(conversationId);
        conversationService.deleteConversation(conversationId);
    }

    // 会话必须存在且属于当前用户；他人会话统一按"会话不存在"处理
    private AgentConversation requireOwnConversation(String conversationId, String userId) {
        AgentConversation conversation = conversationService.getConversation(conversationId)
                .orElseThrow(() -> new BadRequestException("会话不存在: " + conversationId));
        if (userId == null || !userId.equals(conversation.getUserId())) {
            throw new BadRequestException("会话不存在: " + conversationId);
        }
        return conversation;
    }

    // 回答来源快照序列化为 JSON（失败时置空，不影响主流程）
    private String serializeSources(List<SourceItem> sources) {
        if (sources == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            return null;
        }
    }
}
