package com.smartlamp.agent.tools;

import com.smartlamp.agent.Retriever;
import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.LightHistoryDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Alarm;
import com.smartlamp.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.function.Function;

// 工具目录：智能体可用的全部工具定义（名称 / 用途与适用时机 / 输入参数 / 输出结构），
// 生成 OpenAI Function Calling 的 tools 描述，并负责执行工具、给结果打来源标注。
// 来源标注约定：系统真实数据标 system_data，知识库信息标 knowledge，模型不得混淆两者。
// 执行失败时返回结构化错误结果而不是抛异常，保证回答流程不中断。
@Component
public class ToolCatalog {

    private static final int KNOWLEDGE_TOP_K = 2;
    private static final long HISTORY_DEFAULT_WINDOW_MS = 24 * 60 * 60 * 1000L;

    @Autowired
    private AgentTools agentTools;

    @Autowired
    private Retriever retriever;

    @Autowired
    private AgentActionTools agentActionTools;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 工具定义：name 工具名、displayTitle 展示名、source 来源标注、description 用途与适用时机、
    // parameters 输入 schema、executor 执行器（入参为大模型给的 arguments，出参为带 source 标注的结果）、
    // riskLevel 风险等级（READ/LOW_WRITE/HIGH_WRITE）、requiresConfirmation 是否需要用户确认
    public record ToolSpec(String name, String displayTitle, String source, String description,
                           ObjectNode parameters, Function<JsonNode, ObjectNode> executor,
                           String riskLevel, boolean requiresConfirmation) {
    }

    private volatile List<ToolSpec> specs;

    public List<ToolSpec> getSpecs() {
        if (specs == null) {
            synchronized (this) {
                if (specs == null) {
                    specs = buildSpecs();
                }
            }
        }
        return specs;
    }

    private List<ToolSpec> buildSpecs() {
        return List.of(
                new ToolSpec("search_knowledge", "内部知识库", "knowledge",
                        "搜索项目内部知识库（来源标注 knowledge），返回路灯维护、告警处理、光照联动控制等维修知识条目及相关度。当问题涉及通用维护知识、排查步骤、处理建议时使用。",
                        parameters(properties().set("query", prop("string", "检索关键词或用户问题")), "query"),
                        this::searchKnowledge, "READ", false),
                new ToolSpec("get_device_list", "设备列表（系统实时数据）", "system_data",
                        "查询系统实时数据：全部路灯设备列表（编号、位置、在线状态、最新光照、最近心跳时间）。当用户问系统里有哪些设备或整体运行状态时使用。无输入参数。",
                        parameters(properties()),
                        args -> systemDataNode("devices", objectMapper.valueToTree(agentTools.getDeviceList())), "READ", false),
                new ToolSpec("get_device_status", "设备状态（系统实时数据）", "system_data",
                        "查询系统实时数据：单台设备的当前状态。当用户问某台具体设备是否在线、当前状态如何时使用。",
                        parameters(properties().set("deviceCode", prop("string", "设备编号，例如 lamp001")), "deviceCode"),
                        args -> {
                            String code = args.path("deviceCode").asText();
                            DeviceDTO device = agentTools.getDeviceStatus(code);
                            ObjectNode node = systemDataNode();
                            node.set("device", device == null ? objectMapper.nullNode() : objectMapper.valueToTree(device));
                            return node;
                        }, "READ", false),
                new ToolSpec("get_latest_telemetry", "最新光照（系统实时数据）", "system_data",
                        "查询系统实时数据：单台设备的最新光照值。当用户问某台设备当前光照时使用。",
                        parameters(properties().set("deviceCode", prop("string", "设备编号")), "deviceCode"),
                        args -> {
                            String code = args.path("deviceCode").asText();
                            LightDataDTO light = agentTools.getLatestTelemetry(code);
                            ObjectNode node = systemDataNode();
                            node.set("light", light == null ? objectMapper.nullNode() : objectMapper.valueToTree(light));
                            return node;
                        }, "READ", false),
                new ToolSpec("get_telemetry_history", "光照历史（系统实时数据）", "system_data",
                        "查询系统实时数据：单台设备的光照历史曲线。当用户问光照趋势或历史变化时使用。start/end 为毫秒时间戳，缺省取最近24小时。",
                        parameters(properties()
                                .set("deviceCode", prop("string", "设备编号"))
                                .set("start", prop("number", "起始毫秒时间戳（可选）"))
                                .set("end", prop("number", "结束毫秒时间戳（可选）")), "deviceCode"),
                        this::telemetryHistory, "READ", false),
                new ToolSpec("get_alert_history", "告警记录（系统实时数据）", "system_data",
                        "查询系统实时数据：告警记录，可按设备编号过滤。当用户问告警、设备异常、为什么离线时使用。",
                        parameters(properties().set("deviceCode", prop("string", "设备编号（可选，不传查全部）"))),
                        args -> {
                            String code = args.hasNonNull("deviceCode") ? args.path("deviceCode").asText() : null;
                            List<Alarm> alarms = code == null ? agentTools.getAlertHistory() : agentTools.getAlertHistory(code);
                            return systemDataNode("alarms", alarmsToArray(alarms));
                        }, "READ", false),
                new ToolSpec("get_linkage_config", "联动配置（系统实时数据）", "system_data",
                        "查询系统实时数据：当前光照联动配置（自动开关与阈值）。当用户问阈值设置或自动控制配置时使用。无输入参数。",
                        parameters(properties()),
                        args -> systemDataNode("config", objectMapper.valueToTree(agentTools.getLinkageConfig())), "READ", false),
                // ============ 控制意图工具（只生成待确认 Action，绝不执行控制） ============
                new ToolSpec("turn_on_light", "开灯控制请求", "action",
                        "提交单台路灯开灯请求（低风险写操作，需要用户确认）。工具会自动检查：设备是否存在、是否在线、当前开关状态；检查通过后生成待确认 Action，不会真正控制设备。仅支持单台设备，绝不用于批量操作。",
                        parameters(properties().set("deviceCode", prop("string", "设备编号，例如 lamp001")), "deviceCode"),
                        agentActionTools::requestTurnOn, "LOW_WRITE", true),
                new ToolSpec("turn_off_light", "关灯控制请求", "action",
                        "提交单台路灯关灯请求（低风险写操作，需要用户确认）。工具会自动检查：设备是否存在、是否在线、当前开关状态；检查通过后生成待确认 Action，不会真正控制设备。仅支持单台设备，绝不用于批量操作。",
                        parameters(properties().set("deviceCode", prop("string", "设备编号，例如 lamp001")), "deviceCode"),
                        agentActionTools::requestTurnOff, "LOW_WRITE", true),
                new ToolSpec("turn_off_all", "关闭全部设备请求", "action",
                        "提交关闭全部在线路灯的批量请求（低风险写操作，必须用户二次确认后才会执行，绝不自动执行）。仅当用户明确要求批量关闭全部设备时使用；\"全部打开\"等其他批量操作不开放。",
                        parameters(properties()),
                        agentActionTools::requestTurnOffAll, "LOW_WRITE", true),
                new ToolSpec("set_light_threshold", "光照阈值修改请求", "action",
                        "提交光照阈值修改请求（低风险写操作，需要用户确认）。参数 value 必须是指定的明确数值（合法范围 10-500，由后端业务规则定义，你不得自行决定合法范围）。用户只说\"调高一点\"等模糊说法时，不得调用本工具猜测数值：必须先调用 get_linkage_config 查询当前配置，再向用户给出明确候选值，等用户确认具体数值后调用。",
                        parameters(properties().set("value", prop("number", "目标开灯阈值（10-500 之间的明确数值）")), "value"),
                        agentActionTools::requestSetThreshold, "LOW_WRITE", true),
                new ToolSpec("set_auto_mode", "自动模式修改请求", "action",
                        "提交自动控制开关修改请求（低风险写操作，需要用户确认）。参数 enabled=true 开启自动控制（设备按光照阈值自动开关灯），false 关闭自动控制。用户说\"天黑自动开灯\"等需求时，优先引导开启系统已有自动控制能力（本工具或系统配置界面），智能助手不得自己成为长期后台循环控制器。",
                        parameters(properties().set("enabled", prop("boolean", "是否开启自动控制")), "enabled"),
                        agentActionTools::requestSetAutoMode, "LOW_WRITE", true));
    }

    // ============ 执行入口 ============

    // 执行指定工具；未知工具、参数错误、底层查询失败都返回带 source 标注的错误结果
    public ObjectNode execute(String name, JsonNode arguments) {
        ToolSpec spec = getSpecs().stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
        if (spec == null) {
            return errorResult("knowledge", "未知工具: " + name);
        }
        try {
            return spec.executor().apply(arguments == null ? objectMapper.createObjectNode() : arguments);
        } catch (BadRequestException e) {
            return errorResult(spec.source(), "参数错误: " + e.getMessage());
        } catch (Exception e) {
            return errorResult(spec.source(), "查询失败: " + e.getMessage());
        }
    }

    // 生成 OpenAI Function Calling 的 tools 描述
    public ArrayNode toOpenAiTools() {
        ArrayNode tools = objectMapper.createArrayNode();
        for (ToolSpec spec : getSpecs()) {
            ObjectNode tool = tools.addObject();
            tool.put("type", "function");
            ObjectNode fn = tool.putObject("function");
            fn.put("name", spec.name());
            fn.put("description", spec.description());
            fn.set("parameters", spec.parameters());
        }
        return tools;
    }

    // 根据工具名取展示名（用于响应的 sources）
    public String displayTitle(String name) {
        return getSpecs().stream().filter(s -> s.name().equals(name)).map(ToolSpec::displayTitle).findFirst().orElse(name);
    }

    // 根据工具名取来源标注（knowledge / system_data / action）
    public String sourceOf(String name) {
        return getSpecs().stream().filter(s -> s.name().equals(name)).map(ToolSpec::source).findFirst().orElse("system_data");
    }

    // ============ 执行器 ============

    private ObjectNode searchKnowledge(JsonNode args) {
        String query = args.path("query").asText(null);
        if (query == null || query.isBlank()) throw new BadRequestException("query 不能为空");
        List<Retriever.KbMatch> matches = retriever.retrieve(query.trim(), KNOWLEDGE_TOP_K);

        ObjectNode node = objectMapper.createObjectNode();
        node.put("source", "knowledge");
        ArrayNode arr = node.putArray("matches");
        for (Retriever.KbMatch m : matches) {
            ObjectNode item = arr.addObject();
            item.put("title", m.entry().getTitle());
            item.put("category", m.entry().getCategory());
            item.put("content", m.entry().getContent());
            item.put("score", m.score());
        }
        return node;
    }

    private ObjectNode telemetryHistory(JsonNode args) {
        String code = args.path("deviceCode").asText(null);
        if (code == null || code.isBlank()) throw new BadRequestException("deviceCode 不能为空");
        long end = args.hasNonNull("end") ? args.path("end").asLong() : System.currentTimeMillis();
        long start = args.hasNonNull("start") ? args.path("start").asLong() : end - HISTORY_DEFAULT_WINDOW_MS;

        LightHistoryDTO history = agentTools.getTelemetryHistory(code.trim(), start, end);
        return systemDataNode("history", objectMapper.valueToTree(history));
    }

    // ============ 结果构造 ============

    private ObjectNode systemDataNode() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("source", "system_data");
        return node;
    }

    private ObjectNode systemDataNode(String field, JsonNode value) {
        ObjectNode node = systemDataNode();
        node.set(field, value);
        return node;
    }

    private ObjectNode errorResult(String source, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("source", source);
        node.put("error", message);
        return node;
    }

    private ArrayNode alarmsToArray(List<Alarm> alarms) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Alarm a : alarms) {
            ObjectNode item = arr.addObject();
            item.put("id", a.getId());
            item.put("deviceId", a.getDeviceId());
            item.put("type", a.getType());
            item.put("level", a.getLevel());
            item.put("message", a.getMessage());
            item.put("ts", a.getTs());
            item.put("status", a.getStatus());
        }
        return arr;
    }

    // ============ schema 构造 ============

    private ObjectNode properties() {
        return objectMapper.createObjectNode();
    }

    private ObjectNode prop(String type, String description) {
        ObjectNode p = objectMapper.createObjectNode();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    private ObjectNode parameters(ObjectNode properties, String... required) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        if (required.length > 0) {
            ArrayNode req = schema.putArray("required");
            for (String r : required) req.add(r);
        }
        return schema;
    }
}
