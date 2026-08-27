package com.smartlamp.service;

import com.smartlamp.agent.LlmClient;
import com.smartlamp.agent.LlmException;
import com.smartlamp.agent.PromptProvider;
import com.smartlamp.agent.Retriever;
import com.smartlamp.agent.conversation.AgentMessage;
import com.smartlamp.agent.tools.ToolCatalog;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.PendingActionInfo;
import com.smartlamp.dto.SourceItem;
import com.smartlamp.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.format.DateTimeFormatter;
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
        return ask(question, List.of(), null);
    }

    // 多轮版：注入最近历史消息（仅语言上下文，system prompt 在最前，当前问题在最后）
    public AskResponse ask(String question, List<AgentMessage> historyMessages) {
        return ask(question, historyMessages, null);
    }

    // 长对话版：Summary + 最近历史消息 + 当前问题（Summary 仅作背景参考，不代表设备当前状态）
    public AskResponse ask(String question, List<AgentMessage> historyMessages, String summary) {
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
            // 注入对话摘要（若有）：较早历史的压缩背景，实时事实仍必须重新查询
            if (summary != null && !summary.isBlank()) {
                messages.add(message("system", "【对话摘要·仅作背景参考，不代表设备当前状态】\n" + summary));
            }
            // 注入最近历史消息：只用于理解语言上下文（指代消解），不代表设备当前状态
            for (AgentMessage m : historyMessages) {
                String role = "user".equals(m.getRole()) ? "user" : "assistant";
                messages.add(message(role, "[历史消息 " + formatTime(m.getCreatedAt()) + "] " + m.getContent()));
            }
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
            AskResponse response = new AskResponse(answer, buildSources(executed, text));
            response.setAction(buildPendingAction(executed));
            return response;
        } catch (Exception e) {
            // 4. 大模型不可用/流程失败时降级，绝不影响接口可用性
            log.warn("智能体流程失败，降级为本地知识库回答: {}", e.getMessage());
            return localResponse(retriever.retrieve(text, TOP_K));
        }
    }

    // 历史消息时间展示（仅用于标注"这是过去的消息"）
    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private String formatTime(java.time.LocalDateTime time) {
        return time == null ? "" : time.format(HISTORY_TIME_FORMAT);
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
            } else if ("action".equals(toolCatalog.sourceOf(tool.name()))) {
                // 控制请求工具：标注为 action 来源（待确认操作请求）
                sources.add(new SourceItem(toolCatalog.displayTitle(tool.name()), "action", 1.0));
            } else {
                sources.add(new SourceItem(toolCatalog.displayTitle(tool.name()), "system_data", 1.0));
            }
        }
        return sources;
    }

    // 从已执行工具结果中提取待确认操作（阶段21 联调落地）：前端据此在对话中渲染确认按钮。
    // 只在工具结果 status=PENDING_CONFIRMATION 且携带 actionId 时返回；开灯/关灯自动执行不返回。
    private PendingActionInfo buildPendingAction(List<ExecutedTool> executed) {
        for (int i = executed.size() - 1; i >= 0; i--) {
            ExecutedTool tool = executed.get(i);
            JsonNode result = tool.result();
            if ("action".equals(toolCatalog.sourceOf(tool.name()))
                    && "PENDING_CONFIRMATION".equals(result.path("status").asText(null))
                    && !result.path("actionId").asText("").isBlank()) {
                PendingActionInfo info = new PendingActionInfo();
                info.setActionId(result.path("actionId").asText());
                info.setActionType(result.path("actionType").asText(null));
                info.setTargetId(result.path("targetId").asText(""));
                info.setSummary(result.path("summary").asText(
                        result.path("actionType").asText("操作请求")));
                info.setRiskLevel(result.path("riskLevel").asText(null));
                info.setExpiresAt(result.has("expiresAt") ? result.path("expiresAt").asLong() : null);
                info.setStatus("PENDING_CONFIRMATION");
                info.setOriginalState(result.path("originalState").asText(null));
                info.setTargetState(result.path("targetState").asText(null));
                return info;
            }
        }
        return null;
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
