package com.smartlamp.service;

import com.smartlamp.agent.LlmClient;
import com.smartlamp.agent.PromptProvider;
import com.smartlamp.agent.Retriever;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.SourceItem;
import com.smartlamp.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

// AI 运维问答：知识库检索 + 大模型生成自然语言回答；
// 大模型未配置或调用失败时自动降级为本地知识库回答，绝不影响接口可用性
@Slf4j
@Service
public class AgentService {

    private static final int TOP_K = 2;

    @Autowired
    private Retriever retriever;

    @Autowired
    private PromptProvider promptProvider;

    @Autowired
    private LlmClient llmClient;

    public AskResponse ask(String question) {
        // 1. 空问题校验（由 GlobalExceptionHandler 统一返回 code=400）
        if (question == null || question.isBlank()) {
            throw new BadRequestException("question 不能为空");
        }

        String text = question.trim();

        // 2. 知识库检索 Top K
        List<Retriever.KbMatch> matches = retriever.retrieve(text, TOP_K);

        // 3. 大模型调用：未配置或失败时降级为本地知识库回答
        String answer = buildLocalAnswer(matches);
        if (llmClient.isConfigured()) {
            try {
                answer = llmClient.completeChat(promptProvider.get(), buildUserPrompt(text, matches));
            } catch (Exception e) {
                log.warn("大模型调用失败，降级为本地知识库回答: {}", e.getMessage());
            }
        }

        // 4. 组装响应：title=条目标题、section=知识分类、score=命中关键词数
        List<SourceItem> sources = matches.stream()
                .map(match -> new SourceItem(match.entry().getTitle(), match.entry().getCategory(), (double) match.score()))
                .collect(Collectors.toList());
        return new AskResponse(answer, sources);
    }

    // 本地降级回答：拼接命中条目正文；无命中时返回明确提示
    private String buildLocalAnswer(List<Retriever.KbMatch> matches) {
        if (matches.isEmpty()) {
            return "知识库中暂未找到与该问题相关的内容，请补充更多细节后重试。";
        }
        return matches.stream().map(match -> match.entry().getContent()).collect(Collectors.joining(" "));
    }

    // 用户 Prompt：知识库上下文 + 用户问题（与 Node 版格式一致）
    private String buildUserPrompt(String question, List<Retriever.KbMatch> matches) {
        String context = matches.isEmpty()
                ? "（知识库未检索到相关内容）"
                : matches.stream()
                        .map(match -> "【" + match.entry().getCategory() + "】《" + match.entry().getTitle() + "》\n" + match.entry().getContent())
                        .collect(Collectors.joining("\n\n"));
        return "【知识库内容】\n" + context + "\n\n【用户问题】\n" + question;
    }
}
