package com.smartlamp.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// HttpClient 是 final 类无法 mock，用 JDK 内置 HttpServer 起临时端口模拟大模型服务
class LlmClientTest {

    private HttpServer server;
    private int port;
    private String lastRequestBody;
    private String lastAuthHeader;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private LlmClient newClient() {
        LlmClient client = new LlmClient();
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:" + port + "/v1");
        ReflectionTestUtils.setField(client, "model", "mock-model");
        return client;
    }

    // 起一个返回 200 + 指定 JSON 的模拟大模型服务
    private void respondWith(String json) throws IOException {
        server.createContext("/v1/chat/completions", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @Test
    void 正常调用返回内容并携带完整请求体() throws Exception {
        respondWith("{\"choices\":[{\"message\":{\"content\":\"模拟大模型回答\"}}]}");
        LlmClient client = newClient();

        String answer = client.completeChat("你是智慧路灯维护助手。", "【用户问题】路灯离线应该怎么排查？");

        assertThat(answer).isEqualTo("模拟大模型回答");
        assertThat(lastAuthHeader).isEqualTo("Bearer test-key");
        ObjectMapper mapper = new ObjectMapper();
        var body = mapper.readTree(lastRequestBody);
        assertThat(body.path("model").asText()).isEqualTo("mock-model");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(0).path("content").asText()).contains("智慧路灯维护助手");
        assertThat(body.path("messages").get(1).path("content").asText()).contains("路灯离线应该怎么排查");
    }

    @Test
    void 接口返回500时抛出异常() throws IOException {
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        port = server.getAddress().getPort();

        assertThatThrownBy(() -> newClient().completeChat("s", "u"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("500");
    }

    @Test
    void 返回内容为空时抛出异常() throws IOException {
        respondWith("{\"choices\":[{\"message\":{\"content\":\"\"}}]}");

        assertThatThrownBy(() -> newClient().completeChat("s", "u"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("内容为空");
    }

    @Test
    void 接口不可达时抛出异常() throws IOException {
        server.start();
        port = server.getAddress().getPort();
        server.stop(0); // 停掉后端口连接被拒绝

        assertThatThrownBy(() -> newClient().completeChat("s", "u"))
                .isInstanceOf(LlmException.class);
    }

    @Test
    void 工具调用响应解析出toolCalls() throws IOException {
        respondWith("{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_device_status\",\"arguments\":\"{\\\"deviceCode\\\":\\\"lamp001\\\"}\"}}]}}]}");
        LlmClient client = newClient();

        List<ObjectNode> messages = List.of(client.message("user", "lamp001现在在线吗？"));
        LlmClient.ChatResponse response = client.completeChat(messages, new ObjectMapper().createArrayNode());

        assertThat(response.content()).isNull();
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("get_device_status");
        assertThat(response.toolCalls().get(0).arguments().path("deviceCode").asText()).isEqualTo("lamp001");
    }

    @Test
    void 未配置时不可用() {
        assertThat(new LlmClient().isConfigured()).isFalse();
    }

    @Test
    void 配置齐全时可用() throws IOException {
        respondWith("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        assertThat(newClient().isConfigured()).isTrue();
    }
}
