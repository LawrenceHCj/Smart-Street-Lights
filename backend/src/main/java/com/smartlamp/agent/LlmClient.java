package com.smartlamp.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// 大模型调用封装：兼容 OpenAI 接口协议（OpenAI / DeepSeek / Kimi 等），支持 Function Calling，
// 配置走 application.yml 的 llm: 段（环境变量 LLM_API_KEY / LLM_BASE_URL / LLM_MODEL 占位）
@Component
public class LlmClient {

    private static final int TIMEOUT_MS = 10_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${llm.model:}")
    private String model;

    // 一次工具调用：id 用于回填 tool 消息、name 工具名、arguments 模型给出的参数
    public record ToolCall(String id, String name, JsonNode arguments) {
    }

    // 一次对话响应：content 最终回答（可能为 null）、toolCalls 模型请求的工具调用
    public record ChatResponse(String content, List<ToolCall> toolCalls) {
    }

    // 未配置 Key 或模型时视为未启用，由调用方降级为本地知识库回答
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }

    // 单轮对话（不带工具），供简单问答与旧测试使用
    public String completeChat(String system, String user) {
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(message("system", system));
        messages.add(message("user", user));
        ChatResponse response = completeChat(messages, null);
        if (response.content() == null || response.content().isBlank()) {
            throw new LlmException("LLM API 返回内容为空");
        }
        return response.content();
    }

    // 多轮对话（支持工具）：tools 为 OpenAI Function Calling 描述，可为 null
    public ChatResponse completeChat(List<ObjectNode> messages, ArrayNode tools) {
        if (!isConfigured()) throw new LlmException("LLM not configured");

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode bodyMessages = body.putArray("messages");
        bodyMessages.addAll(messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
        }
        body.put("temperature", 0.3);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new LlmException("LLM API 返回 " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode msg = root.path("choices").path(0).path("message");

            String content = msg.path("content").isNull() ? null : msg.path("content").asText(null);
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode c : msg.path("tool_calls")) {
                JsonNode fn = c.path("function");
                calls.add(new ToolCall(
                        c.path("id").asText(),
                        fn.path("name").asText(),
                        objectMapper.readTree(fn.path("arguments").asText("{}"))));
            }
            return new ChatResponse(content == null ? null : content.trim(), calls);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM API 调用失败: " + e.getMessage());
        }
    }

    // 构造一条对话消息
    public ObjectNode message(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        if (content != null) node.put("content", content);
        return node;
    }
}
