package com.smartlamp.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

// 关键词检索器：命中关键词计数评分，同分保持知识库原始顺序（稳定排序），与 Node 版行为一致；
// 后续替换为向量检索时保持 retrieve 接口不变
@Component
public class Retriever {

    private static final int DEFAULT_LIMIT = 2;

    @Autowired
    private KnowledgeBase knowledgeBase;

    // 一条匹配结果：知识条目 + 相关度（命中关键词数）
    public record KbMatch(KnowledgeEntry entry, int score) {
    }

    public List<KbMatch> retrieve(String question) {
        return retrieve(question, DEFAULT_LIMIT);
    }

    public List<KbMatch> retrieve(String question, int limit) {
        String text = question == null ? "" : question.trim();
        if (text.isEmpty()) return List.of();

        return knowledgeBase.findAll().stream()
                .map(entry -> new KbMatch(entry, (int) entry.getKeywords().stream().filter(text::contains).count()))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(KbMatch::score).reversed())
                .limit(limit)
                .toList();
    }
}
