package com.smartlamp.agent;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// 加载 prompts.md 作为大模型 System Prompt（内容与 Node 版一致，含文件头）
@Component
public class PromptProvider {

    private final String systemPrompt;

    public PromptProvider() {
        try (InputStream in = new ClassPathResource("prompts.md").getInputStream()) {
            this.systemPrompt = StreamUtils.copyToString(in, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            throw new IllegalStateException("加载 System Prompt 资源 prompts.md 失败", e);
        }
    }

    public String get() {
        return systemPrompt;
    }
}
