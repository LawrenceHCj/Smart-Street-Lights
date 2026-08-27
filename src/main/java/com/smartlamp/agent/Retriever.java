package com.smartlamp.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 知识检索器（中文分词 + BM25，零外部依赖）：对 title/keywords/category/content 四字段建倒排索引
// 并按 BM25 加权打分（见 Bm25Index）；词元化由 Segmenter 完成（领域词典最大匹配 + 二字切分兜底）。
// 结果过滤规则：命中至少 2 个不同词元，或任一命中来自 keywords 人工词表——避免单个噪声 bigram
// 造成误召回（如"今天天气怎么样"不会命中知识库）。
// 后续升级向量检索时保持 retrieve 接口不变，仅替换本实现。
@Component
public class Retriever {

    private static final int DEFAULT_LIMIT = 2;
    private static final int MIN_DISTINCT_TOKENS = 2;

    // 领域补充词表：keywords 之外的高频口语/领域词（与知识库 keywords 合并构成分词词典；单字不入词典，由二字切分覆盖）
    private static final Set<String> EXTRA_DICTIONARY =
            Set.of("路灯", "排查", "开关", "在线", "光照", "配置", "设备");

    @Autowired
    private KnowledgeBase knowledgeBase;

    private volatile Bm25Index index;

    // 一条匹配结果：知识条目 + BM25 相关度分
    public record KbMatch(KnowledgeEntry entry, double score) {
    }

    public List<KbMatch> retrieve(String question) {
        return retrieve(question, DEFAULT_LIMIT);
    }

    public List<KbMatch> retrieve(String question, int limit) {
        String text = question == null ? "" : question.trim();
        if (text.isEmpty()) return List.of();

        Bm25Index idx = ensureIndex();
        List<String> tokens = idx.segmenter().tokenize(text);
        if (tokens.isEmpty()) return List.of();

        // 过滤噪声（单噪声词元/无词表命中不召回）→ 按相关度取 top-N
        return idx.search(tokens).stream()
                .filter(hit -> hit.keywordHit() || hit.distinctTokens() >= MIN_DISTINCT_TOKENS)
                .limit(limit)
                .map(hit -> new KbMatch(hit.entry(), hit.score()))
                .toList();
    }

    // 首次检索时构建索引：词典 = 知识库全部 keywords + 领域补充词表
    private Bm25Index ensureIndex() {
        if (index == null) {
            synchronized (this) {
                if (index == null) {
                    List<KnowledgeEntry> entries = knowledgeBase.findAll();
                    Set<String> dictionary = new HashSet<>(EXTRA_DICTIONARY);
                    for (KnowledgeEntry entry : entries) {
                        dictionary.addAll(entry.getKeywords());
                    }
                    index = new Bm25Index(entries, new Segmenter(dictionary));
                }
            }
        }
        return index;
    }
}
