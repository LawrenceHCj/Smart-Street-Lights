package com.smartlamp.service;

import com.smartlamp.agent.LlmClient;
import com.smartlamp.agent.LlmException;
import com.smartlamp.agent.PromptProvider;
import com.smartlamp.agent.Retriever;
import com.smartlamp.agent.conversation.AgentMessage;
import com.smartlamp.agent.tools.AgentTools;
import com.smartlamp.agent.tools.ToolCatalog;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.dto.PendingActionInfo;
import com.smartlamp.dto.SourceItem;
import com.smartlamp.entity.Alarm;
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

    // 本地降级兜底（LLM 不可用时按关键词规则查询系统真实数据）
    @Autowired
    private AgentTools agentTools;

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

        // 2. 设备实时状态类问题（"哪些/现在/当前" + 在线/灯/光照/告警/阈值话题）走确定性直答：
        //    由系统数据规则直接回答，不依赖大模型可用性——知识库只有维护排查知识，
        //    答不了"当前事实"，且大模型网络抖动时此类问题仍能稳定回答
        try {
            AskResponse direct = directRealtimeAnswer(text);
            if (direct != null) return direct;
        } catch (Exception e) {
            log.warn("实时状态直答失败，回退常规流程: {}", e.getMessage());
        }

        // 3. 大模型未配置时直接走本地知识库回答
        if (!llmClient.isConfigured()) {
            return localResponse(retriever.retrieve(text, TOP_K), text);
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
            return localResponse(retriever.retrieve(text, TOP_K), text);
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

    // 本地降级回答（LLM 不可用时的确定性兜底）：
    // 1. 设备实时状态类问题（"哪些/现在/当前" + 在线/灯/光照/告警/阈值话题）→ 直接查系统真实数据
    //    ——知识库只有维护排查知识，不可能回答"当前事实"，且不命中时先不输出"没找到"；
    // 2. 其余问题知识库优先：命中 → 拼接命中条目正文；
    // 3. 知识库无命中 → 按关键词规则查询系统真实数据兜底（设备状态/光照/告警/配置）；
    // 4. 均无结果 → 明确提示未找到（绝不编造）。
    private AskResponse localResponse(List<Retriever.KbMatch> matches, String question) {
        if (isRealtimeQuestion(question)) {
            AskResponse dataAnswer = systemDataFallback(question);
            if (dataAnswer != null) return dataAnswer;
        }
        if (!matches.isEmpty()) {
            String answer = matches.stream().map(m -> m.entry().getContent()).collect(Collectors.joining(" "));
            List<SourceItem> sources = matches.stream()
                    .map(m -> new SourceItem(m.entry().getTitle(), "knowledge", (double) m.score()))
                    .collect(Collectors.toList());
            return new AskResponse(answer, sources);
        }
        AskResponse dataAnswer = systemDataFallback(question);
        if (dataAnswer != null) return dataAnswer;
        return new AskResponse(
                "未找到相关信息：知识库与系统数据中均未找到与该问题相关的内容，请换个问法或补充更多细节。",
                List.of());
    }

    // 实时状态类问题判断：问"当前事实"（哪些/现在/当前/几台…）+ 设备/数据话题
    private boolean isRealtimeQuestion(String question) {
        boolean ask = containsAny(question, "哪些", "哪个", "几台", "多少", "现在", "当前", "有没有");
        boolean topic = containsAny(question, "在线", "离线", "灯", "路灯", "设备",
                "光照", "亮度", "勒克斯", "lux", "告警", "报警", "阈值", "自动", "联动");
        return ask && topic;
    }

    // 主链路确定性直答入口：实时状态类问题直接查系统数据（规则不命中返回 null 走常规流程）
    private AskResponse directRealtimeAnswer(String question) {
        if (!isRealtimeQuestion(question)) return null;
        return systemDataFallback(question);
    }

    // ============ 系统数据兜底（确定性规则，仅陈述真实查询结果） ============

    private boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    // 按关键词规则回答设备状态类问题；规则不命中返回 null（由上层提示未找到）
    private AskResponse systemDataFallback(String question) {
        List<DeviceDTO> devices = agentTools.getDeviceList();
        if (devices == null) devices = List.of();
        boolean lampTopic = containsAny(question, "灯", "路灯", "设备");
        boolean askWords = containsAny(question, "哪些", "哪个", "几台", "什么", "现在", "当前", "状态", "有没有");
        boolean onState = containsAny(question, "开着的", "是开的", "开着", "打开的", "是打开的", "点亮", "亮着");
        boolean offState = containsAny(question, "关着的", "是关的", "关着", "关闭的", "是关闭的", "熄灭");

        // 1. 在线/离线状态
        if (containsAny(question, "在线", "离线", "上线", "掉线")) {
            return deviceAnswer(onlineStatusAnswer(devices));
        }
        // 2. 灯开关状态（问"哪些灯是开着的"这类）
        if (lampTopic && askWords && (onState || offState)) {
            return deviceAnswer(lampStatusAnswer(devices, onState));
        }
        // 3. 光照值
        if (containsAny(question, "光照", "亮度", "勒克斯", "lux")) {
            return deviceAnswer(luxAnswer(devices));
        }
        // 4. 告警
        if (containsAny(question, "告警", "报警", "异常")) {
            return new AskResponse(alarmAnswer(),
                    List.of(new SourceItem("告警记录（系统实时数据）", "system_data", 1.0)));
        }
        // 5. 联动配置
        if (containsAny(question, "阈值", "自动", "联动", "滞回")) {
            return new AskResponse(configAnswer(),
                    List.of(new SourceItem("联动配置（系统实时数据）", "system_data", 1.0)));
        }
        return null;
    }

    private AskResponse deviceAnswer(String answer) {
        return new AskResponse(answer,
                List.of(new SourceItem("设备列表（系统实时数据）", "system_data", 1.0)));
    }

    private String deviceName(DeviceDTO d) {
        return d.getCode() + (d.getLocation() != null && !d.getLocation().isBlank()
                ? "（" + d.getLocation() + "）" : "");
    }

    // 在线/离线汇总（按 status 如实统计）
    private String onlineStatusAnswer(List<DeviceDTO> devices) {
        List<String> online = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        for (DeviceDTO d : devices) {
            ("ONLINE".equals(d.getStatus()) ? online : offline).add(deviceName(d));
        }
        return "系统实时数据：当前共 " + devices.size() + " 台设备，在线 " + online.size() + " 台"
                + (online.isEmpty() ? "。" : "：" + String.join("、", online) + "。")
                + (offline.isEmpty() ? "" : "离线 " + offline.size() + " 台：" + String.join("、", offline) + "。")
                + "\n信息来源：系统实时数据（设备列表）。";
    }

    // 灯开关状态汇总（按 lampStatus 如实统计；未上报的单独说明）
    private String lampStatusAnswer(List<DeviceDTO> devices, boolean onTopic) {
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (DeviceDTO d : devices) {
            if ("ON".equals(d.getLampStatus())) on.add(deviceName(d));
            else if ("OFF".equals(d.getLampStatus())) off.add(deviceName(d));
            else unknown.add(deviceName(d));
        }
        if (onTopic) {
            return "系统实时数据：当前灯打开的共 " + on.size() + " 台"
                    + (on.isEmpty() ? "。" : "：" + String.join("、", on) + "。")
                    + (off.isEmpty() ? "" : "灯关闭的 " + off.size() + " 台：" + String.join("、", off) + "。")
                    + (unknown.isEmpty() ? "" : "未上报灯状态的 " + unknown.size() + " 台：" + String.join("、", unknown) + "。")
                    + "\n信息来源：系统实时数据（设备列表）。";
        }
        return "系统实时数据：当前灯关闭的共 " + off.size() + " 台"
                + (off.isEmpty() ? "。" : "：" + String.join("、", off) + "。")
                + (on.isEmpty() ? "" : "灯打开的 " + on.size() + " 台：" + String.join("、", on) + "。")
                + (unknown.isEmpty() ? "" : "未上报灯状态的 " + unknown.size() + " 台：" + String.join("、", unknown) + "。")
                + "\n信息来源：系统实时数据（设备列表）。";
    }

    // 各设备最新光照值（无数据的设备单独说明）
    private String luxAnswer(List<DeviceDTO> devices) {
        List<String> parts = new ArrayList<>();
        List<String> none = new ArrayList<>();
        for (DeviceDTO d : devices) {
            if (d.getLatestLux() == null) none.add(d.getCode());
            else parts.add(deviceName(d) + " " + d.getLatestLux() + " 勒克斯");
        }
        return "系统实时数据：各设备最新光照值——" + String.join("；", parts) + "。"
                + (none.isEmpty() ? "" : none.size() + " 台设备暂无光照数据：" + String.join("、", none) + "。")
                + "\n信息来源：系统实时数据（设备列表）。";
    }

    // 告警汇总（区分未恢复与已恢复，不把历史告警说成当前事实）
    private String alarmAnswer() {
        List<Alarm> alarms = agentTools.getAlertHistory();
        if (alarms == null) alarms = List.of();
        long active = alarms.stream().filter(a -> "OPEN".equals(a.getStatus()) || "ACTIVE".equals(a.getStatus())).count();
        long recovered = alarms.size() - active;
        return "系统实时数据：共 " + alarms.size() + " 条告警记录，其中未恢复 " + active
                + " 条、已恢复 " + recovered + " 条。\n信息来源：系统实时数据（告警记录）。";
    }

    // 联动配置汇总（如实读取当前配置）
    private String configAnswer() {
        LinkageConfigDTO config = agentTools.getLinkageConfig();
        if (config == null) return "系统实时数据：当前联动配置不可用。\n信息来源：系统实时数据（联动配置）。";
        return "系统实时数据：自动控制当前" + (config.isEnabled() ? "已开启" : "已关闭")
                + "，开灯阈值 " + config.getThreshold() + " 勒克斯，滞回值 " + config.getHysteresis()
                + "。\n信息来源：系统实时数据（联动配置）。";
    }
}
