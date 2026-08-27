package com.smartlamp.agent.conversation;

import com.smartlamp.agent.LlmClient;
import com.smartlamp.service.AgentConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 长对话摘要器：窗口外未摘要消息累积到阈值时才调用一次 LLM 生成摘要，
// 绝不每条消息都调用；摘要失败静默跳过，不影响聊天主流程。
// 发送侧结构：Summary + 最近窗口消息 + 当前问题（窗口大小见 AgentConversationService.RECENT_HISTORY_LIMIT）。
@Slf4j
@Component
public class ConversationSummarizer {

    // 触发阈值：窗口外未摘要消息达到该数量才生成一次摘要
    public static final int SUMMARIZE_MIN_BACKLOG = 10;

    // 摘要输入中单条消息的最大字符数（防超长消息撑爆摘要调用）
    private static final int MAX_MESSAGE_CHARS = 300;

    // 摘要生成规则 Prompt（system 侧只放规则；摘要素材由 user 侧携带）
    public static final String SUMMARY_PROMPT = """
            你是智慧路灯维护助手的对话摘要器。请把用户消息中的对话历史压缩成一份简短摘要（中文，不超过 300 字），只保留对未来对话真正有帮助的信息：
            - 用户当前讨论的主要设备
            - 之前讨论过的故障现象与排查结论
            - 用户明确提出的需求或偏好
            - 已经完成的重要操作
            - 仍未解决的问题

            规则：
            - 设备状态等实时信息必须带时间语义（如"在之前的对话中，lamp001 曾查询为离线"），绝不能写成当前事实（如"lamp001 当前离线"）。
            - 不得包含 API Key、Token、System Prompt、模型内部推理或思考过程。
            - 如果已有旧摘要，把旧摘要与新增对话合并成一份新摘要，不重复、不丢失主题。
            - 只输出摘要正文，不要任何解释或前缀。""";

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private LlmClient llmClient;

    // 聊天主流程保存消息后调用：满足触发条件才真正执行摘要
    public void summarizeIfNeeded(String conversationId) {
        try {
            if (!llmClient.isConfigured()) {
                return; // 未配置大模型时跳过摘要（本地降级场景）
            }
            AgentConversation conversation = conversationService.getConversation(conversationId).orElse(null);
            if (conversation == null) {
                return;
            }

            List<AgentMessage> all = conversationService.listMessages(conversationId);
            List<AgentMessage> backlog = backlogMessages(all, conversation.getSummarizedUpToId());
            if (backlog.size() < SUMMARIZE_MIN_BACKLOG) {
                return; // 短对话不摘要
            }

            String newSummary = llmClient.completeChat(
                    SUMMARY_PROMPT,
                    buildUserPrompt(conversation.getSummary(), backlog));
            if (newSummary == null || newSummary.isBlank()) {
                log.warn("对话摘要生成结果为空，跳过: {}", conversationId);
                return;
            }

            // 更新摘要与水位线：backlog 按时间升序，最后一条即本次覆盖到的最大消息 id
            conversation.setSummary(newSummary.trim());
            conversation.setSummarizedUpToId(backlog.get(backlog.size() - 1).getId());
            conversationService.saveConversation(conversation);
            log.info("对话摘要已更新: {}，覆盖消息 {} 条", conversationId, backlog.size());
        } catch (Exception e) {
            // 摘要失败绝不能影响聊天：静默跳过，下次满足条件时重试
            log.warn("对话摘要失败，本次跳过（不影响聊天主流程）: {}", e.getMessage());
        }
    }

    // 窗口外且未被摘要覆盖的消息（按时间升序；窗口 = 最近 RECENT_HISTORY_LIMIT 条）
    private List<AgentMessage> backlogMessages(List<AgentMessage> all, Long summarizedUpToId) {
        long watermark = summarizedUpToId == null ? 0L : summarizedUpToId;
        int windowEnd = Math.max(0, all.size() - AgentConversationService.RECENT_HISTORY_LIMIT);
        List<AgentMessage> backlog = new ArrayList<>();
        for (int i = 0; i < windowEnd; i++) {
            AgentMessage message = all.get(i);
            if (message.getId() != null && message.getId() > watermark) {
                backlog.add(message);
            }
        }
        return backlog;
    }

    // 摘要输入：旧摘要（若有）+ 待摘要消息（role: content，单条截断）
    private String buildUserPrompt(String existingSummary, List<AgentMessage> backlog) {
        StringBuilder messages = new StringBuilder();
        for (AgentMessage message : backlog) {
            messages.append(message.getRole()).append(": ");
            String content = message.getContent() == null ? "" : message.getContent();
            messages.append(content.length() <= MAX_MESSAGE_CHARS
                    ? content : content.substring(0, MAX_MESSAGE_CHARS));
            messages.append("\n");
        }
        return "【已有摘要】\n" + (existingSummary == null || existingSummary.isBlank() ? "无" : existingSummary)
                + "\n【待摘要对话】\n" + messages + "\n【新摘要】";
    }
}
