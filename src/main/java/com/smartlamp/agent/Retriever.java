package com.smartlamp.agent;

import com.smartlamp.dto.SourceItem;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Retriever {

    private final BM25Retriever bm25 = new BM25Retriever();

    // 模拟知识库：标题 -> 内容（后续可替换为数据库或文件）
    private final Map<String, String> knowledgeBase = new LinkedHashMap<>();
    {
        knowledgeBase.put("路灯常见故障排查手册", "供电异常 通信模块故障 传感器损坏 网关离线 路灯不亮 常见原因");
        knowledgeBase.put("设备维护指南", "定期巡检 清洁灯罩 检查线路 预防性维护 电压电流检测");
        knowledgeBase.put("智能调光策略", "光照阈值 自动控制 节能 开灯 关灯 亮度调节");
    }

    public List<SourceItem> retrieve(String question) {
        int totalDocs = knowledgeBase.size();
        double avgLength = knowledgeBase.values().stream()
                .mapToInt(doc -> tokenize(doc).length)
                .average().orElse(1.0);

        Map<String, Integer> docFreq = computeDocFreq(knowledgeBase.values());

        List<Map.Entry<String, Double>> scored = new ArrayList<>();
        for (Map.Entry<String, String> entry : knowledgeBase.entrySet()) {
            String doc = entry.getValue();
            int docLength = tokenize(doc).length;
            double score = bm25.score(question, doc, docFreq, totalDocs, docLength, avgLength);
            scored.add(new AbstractMap.SimpleEntry<>(entry.getKey(), score));
        }

        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<SourceItem> sources = new ArrayList<>();
        for (Map.Entry<String, Double> item : scored) {
            if (item.getValue() > 0) {
                sources.add(new SourceItem(item.getKey(), "knowledge", item.getValue()));
            }
        }
        return sources;
    }

    private String[] tokenize(String text) {
        return text.toLowerCase().split("[\\s，。！？；:,.!?;]+");
    }

    private Map<String, Integer> computeDocFreq(Collection<String> docs) {
        Map<String, Integer> df = new HashMap<>();
        for (String doc : docs) {
            Set<String> unique = new HashSet<>(Arrays.asList(tokenize(doc)));
            for (String term : unique) {
                df.merge(term, 1, Integer::sum);
            }
        }
        return df;
    }
}