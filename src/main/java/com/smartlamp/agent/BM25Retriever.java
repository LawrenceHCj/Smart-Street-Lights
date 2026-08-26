package com.smartlamp.agent;

import java.util.*;
import java.util.regex.Pattern;

public class BM25Retriever {

    private final double k1 = 1.5;
    private final double b = 0.75;

    /**
     * 计算 BM25 得分
     *
     * @param query         查询语句（可包含空格分词，中文按字符或已有分词结果）
     * @param doc           文档内容
     * @param docFreq       每个词在多少文档中出现
     * @param totalDocs     总文档数
     * @param docLength     当前文档长度（词数）
     * @param avgDocLength  平均文档长度
     * @return 相关度得分
     */
    public double score(String query, String doc, Map<String, Integer> docFreq, int totalDocs, int docLength, double avgDocLength) {
        String[] terms = tokenize(query);
        double score = 0.0;
        for (String term : terms) {
            int tf = termFrequency(doc, term);
            if (tf == 0) continue;
            int df = docFreq.getOrDefault(term, 0);
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);
            double tfComponent = (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * docLength / avgDocLength));
            score += idf * tfComponent;
        }
        return score;
    }

    private String[] tokenize(String text) {
        // 简单按空格和常见标点分词，中文场景可替换为 IK Analyzer 等分词器
        return text.toLowerCase().split("[\\s，。！？；:,.!?;]+");
    }

    private int termFrequency(String doc, String term) {
        String[] words = tokenize(doc);
        int count = 0;
        for (String w : words) {
            if (w.equals(term)) count++;
        }
        return count;
    }
}