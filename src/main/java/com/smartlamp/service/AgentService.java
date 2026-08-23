package com.smartlamp.service;

import com.smartlamp.agent.LlmClient;
import com.smartlamp.agent.LlmException;
import com.smartlamp.agent.PromptProvider;
import com.smartlamp.agent.Retriever;
import com.smartlamp.agent.tools.ToolCatalog;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.SourceItem;
import com.smartlamp.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// AI 运维问答（真实 Agent）：大模型通过 Function Calling 自动选择工具
// —— 需要维修知识时调 search_knowledge（knowledge），需要真实系统数据时调系统数据工具（system_data）。
// 工具结果回填后由大模型综合生成回答；大模型未配置或流程失败时降级为本地知识库回答。
@Slf4j
@Service
public class AgentService {

    private static final int TOP_K = 2;
    private static final int MAX_TOOL_ROUNDS = 3;

    @Autowired
    private Retriever retriever;

    @Autowired
    private PromptProvider promptProvider;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ToolCatalog toolCatalog;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 一次已执行的工具调用（用于回答结束后组装 sources）
    private record ExecutedTool(String name, JsonNode result) {
    }

    public AskResponse ask(String question) {
        // 1. 空问题校验（由 GlobalExceptionHandler 统一返回 code=400）
        if (question == null || question.isBlank()) {
            throw new BadRequestException("question 不能为空");
        }

        String text = question.trim();

        // 2. 大模型未配置时直接走本地知识库回答
        if (!llmClient.isConfigured()) {
            return localResponse(retriever.retrieve(text, TOP_K));
        }

        // 3. Agent 循环：模型选工具 → 执行 → 结果回填 → 模型综合回答
        try {
            List<ObjectNode> messages = new ArrayList<>();
            messages.add(message("system", promptProvider.get()));
            messages.add(message("user", "【用户问题】\n" + text));
            ArrayNode tools = toolCatalog.toOpenAiTools();
            List<ExecutedTool> executed = new ArrayList<>();

            String answer = null;
            for (int round = 0; round < MAX_TOOL_ROUNDS && answer == null; round++) {
                LlmClient.ChatResponse response = llmClient.completeChat(messages, tools);
                if (response.toolCalls().isEmpty()) {
                    answer = response.content();
                    break;
                }
                messages.add(buildAssistantToolCallsMessage(response.toolCalls()));
                for (LlmClient.ToolCall call : response.toolCalls()) {
                    JsonNode result = toolCatalog.execute(call.name(), call.arguments());
                    executed.add(new ExecutedTool(call.name(), result));
                    ObjectNode toolMessage = message("tool", result.toString());
                    toolMessage.put("tool_call_id", call.id());
                    messages.add(toolMessage);
                }
            }

            if (answer == null || answer.isBlank()) {
                throw new LlmException("LLM API 返回内容为空");
            }
            return new AskResponse(answer, buildSources(executed, text));
        } catch (Exception e) {
            // 4. 大模型不可用/流程失败时降级，绝不影响接口可用性
            log.warn("智能体流程失败，降级为本地知识库回答: {}", e.getMessage());
            return localResponse(retriever.retrieve(text, TOP_K));
        }
    }

    // 构造一条对话消息
    private ObjectNode message(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        if (content != null) node.put("content", content);
        return node;
    }

    // 把模型请求的工具调用回写为 assistant 消息（OpenAI 协议要求原样回填 tool_calls）
    private ObjectNode buildAssistantToolCallsMessage(List<LlmClient.ToolCall> calls) {
        ObjectNode assistant = message("assistant", null);
        ArrayNode toolCalls = assistant.putArray("tool_calls");
        for (LlmClient.ToolCall call : calls) {
            ObjectNode item = toolCalls.addObject();
            item.put("id", call.id());
            item.put("type", "function");
            ObjectNode fn = item.putObject("function");
            fn.put("name", call.name());
            fn.put("arguments", call.arguments().toString());
        }
        return assistant;
    }

    // 组装 sources：知识库工具 → 每条匹配知识一个来源（section=knowledge）；
    // 系统数据工具 → 一个来源（section=system_data）；web 来源类型预留
    private List<SourceItem> buildSources(List<ExecutedTool> executed, String question) {
        if (executed.isEmpty()) {
            // 模型未调任何工具：附带本地检索到的知识作为来源
            return retriever.retrieve(question, TOP_K).stream()
                    .map(m -> new SourceItem(m.entry().getTitle(), "knowledge", (double) m.score()))
                    .collect(Collectors.toList());
        }

        List<SourceItem> sources = new ArrayList<>();
        for (ExecutedTool tool : executed) {
            if ("search_knowledge".equals(tool.name())) {
                for (JsonNode match : tool.result().path("matches")) {
                    sources.add(new SourceItem(
                            match.path("title").asText(),
                            "knowledge",
                            match.path("score").asDouble()));
                }
            } else {
                sources.add(new SourceItem(toolCatalog.displayTitle(tool.name()), "system_data", 1.0));
            }
        }
        return sources;
    }

    // 本地降级回答：拼接命中条目正文；无命中时返回明确提示
    private AskResponse localResponse(List<Retriever.KbMatch> matches) {
        String answer;
        List<SourceItem> sources;
        if (matches.isEmpty()) {
            answer = "知识库中暂未找到与该问题相关的内容，请补充更多细节后重试。";
            sources = List.of();
        } else {
            answer = matches.stream().map(m -> m.entry().getContent()).collect(Collectors.joining(" "));
            sources = matches.stream()
                    .map(m -> new SourceItem(m.entry().getTitle(), "knowledge", (double) m.score()))
                    .collect(Collectors.toList());
        }
        return new AskResponse(answer, sources);
    }
}
