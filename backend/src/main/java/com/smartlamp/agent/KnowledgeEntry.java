package com.smartlamp.agent;

import lombok.Data;

import java.util.List;

// 知识条目标题/分类/正文/关键词/来源，与 Node 版 knowledgeBase.js 字段一一对应
@Data
public class KnowledgeEntry {
    private String id;
    private String title;
    private String category;
    private String content;
    private List<String> keywords;
    private String source;
}
