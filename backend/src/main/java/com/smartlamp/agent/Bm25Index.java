package com.smartlamp.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// BM25 倒排索引（零外部依赖）：对知识条目的 title/keywords/category/content 四个字段分别
// 建倒排表并加权打分。词元化由 Segmenter 完成；数据规模增大需要向量检索时整体替换本类，
// 对外检索接口（Retriever.retrieve）保持不变。
public class Bm25Index {

    // 检索字段与权重：keywords 是人工维护词表（命中可信度最高），title 次之，content 兜底
    public enum Field {
        TITLE(3.0), KEYWORDS(3.0), CATEGORY(1.5), CONTENT(1.0);

        final double boost;

        Field(double boost) {
            this.boost = boost;
        }
    }

    private static final double K1 = 1.5; // BM25 词频饱和参数
    private static final double B = 0.75;  // 长度归一化参数

    private final Segmenter segmenter;
    private final List<KnowledgeEntry> docs = new ArrayList<>();
    // 倒排表：词元 → 文档序号 → 字段 → 词频
    private final Map<String, Map<Integer, EnumMap<Field, Integer>>> postings = new HashMap<>();
    // 各文档各字段的总词元数（BM25 长度归一化用）
    private final List<EnumMap<Field, Integer>> fieldLengths = new ArrayList<>();
    private final EnumMap<Field, Double> avgLengths = new EnumMap<>(Field.class);

    public Bm25Index(List<KnowledgeEntry> entries, Segmenter segmenter) {
        this.segmenter = segmenter;
        for (KnowledgeEntry entry : entries) {
            index(entry);
        }
        for (Field field : Field.values()) {
            long sum = 0;
            for (EnumMap<Field, Integer> lengths : fieldLengths) {
                sum += lengths.getOrDefault(field, 0);
            }
            avgLengths.put(field, Math.max(1.0, (double) sum / Math.max(1, fieldLengths.size())));
        }
    }

    public Segmenter segmenter() {
        return segmenter;
    }

    public int docCount() {
        return docs.size();
    }

    // 一条命中：文档 + BM25 分 + 命中的不同词元数 + 是否命中 keywords 字段
    public record Hit(KnowledgeEntry entry, double score, int distinctTokens, boolean keywordHit) {
    }

    // 检索：按 BM25 分降序返回全部命中（过滤与取 top-N 由 Retriever 决定）
    public List<Hit> search(List<String> queryTokens) {
        Set<String> uniqueTerms = new LinkedHashSet<>(queryTokens);
        double[] scores = new double[docs.size()];
        int[] distinct = new int[docs.size()];
        boolean[] keywordHit = new boolean[docs.size()];

        for (String term : uniqueTerms) {
            Map<Integer, EnumMap<Field, Integer>> posting = postings.get(term);
            if (posting == null) continue;
            int df = posting.size();
            double idf = Math.log(1.0 + (docs.size() - df + 0.5) / (df + 0.5));
            for (Map.Entry<Integer, EnumMap<Field, Integer>> p : posting.entrySet()) {
                int docId = p.getKey();
                distinct[docId]++;
                for (Map.Entry<Field, Integer> fe : p.getValue().entrySet()) {
                    Field field = fe.getKey();
                    int tf = fe.getValue();
                    if (field == Field.KEYWORDS) keywordHit[docId] = true;
                    double dl = fieldLengths.get(docId).get(field);
                    double avdl = avgLengths.get(field);
                    double norm = tf * (K1 + 1) / (tf + K1 * (1 - B + B * dl / avdl));
                    scores[docId] += field.boost * idf * norm;
                }
            }
        }

        List<Hit> hits = new ArrayList<>();
        for (int d = 0; d < docs.size(); d++) {
            if (scores[d] > 0) {
                hits.add(new Hit(docs.get(d), scores[d], distinct[d], keywordHit[d]));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits;
    }

    private void index(KnowledgeEntry entry) {
        int docId = docs.size();
        docs.add(entry);
        EnumMap<Field, Integer> lengths = new EnumMap<>(Field.class);
        fieldLengths.add(lengths);

        Map<Field, List<String>> fieldTokens = new EnumMap<>(Field.class);
        fieldTokens.put(Field.TITLE, segmenter.tokenize(entry.getTitle()));
        fieldTokens.put(Field.CATEGORY, segmenter.tokenize(entry.getCategory()));
        fieldTokens.put(Field.CONTENT, segmenter.tokenize(entry.getContent()));
        List<String> keywordTokens = new ArrayList<>();
        if (entry.getKeywords() != null) {
            for (String keyword : entry.getKeywords()) {
                keywordTokens.addAll(segmenter.tokenize(keyword));
            }
        }
        fieldTokens.put(Field.KEYWORDS, keywordTokens);

        for (Field field : Field.values()) {
            List<String> tokens = fieldTokens.get(field);
            lengths.put(field, tokens.size());
            Map<String, Integer> tf = new HashMap<>();
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                postings.computeIfAbsent(e.getKey(), k -> new HashMap<>())
                        .computeIfAbsent(docId, k -> new EnumMap<>(Field.class))
                        .put(field, e.getValue());
            }
        }
    }
}
