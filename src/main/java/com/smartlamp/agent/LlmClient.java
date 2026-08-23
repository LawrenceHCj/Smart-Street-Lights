package com.smartlamp.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// 大模型调用封装：兼容 OpenAI 接口协议（OpenAI / DeepSeek / Kimi 等），
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

    // 未配置 Key 或模型时视为未启用，由调用方降级为本地知识库回答
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }

    public String completeChat(String system, String user) {
        if (!isConfigured()) throw new LlmException("LLM not configured");

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
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

            String content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new LlmException("LLM API 返回内容为空");
            }
            return content.trim();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM API 调用失败: " + e.getMessage());
        }
    }
}
