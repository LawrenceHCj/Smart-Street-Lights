package com.smartlamp.agent;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

// 加载 kb/knowledge-base.json 中的全部知识条目；资源缺失时启动失败，避免线上静默空库
@Component
public class KnowledgeBase {

    private final List<KnowledgeEntry> entries;

    public KnowledgeBase() {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource("kb/knowledge-base.json").getInputStream()) {
            this.entries = objectMapper.readValue(in, new TypeReference<List<KnowledgeEntry>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("加载知识库资源 kb/knowledge-base.json 失败", e);
        }
    }

    public List<KnowledgeEntry> findAll() {
        return entries;
    }
}
