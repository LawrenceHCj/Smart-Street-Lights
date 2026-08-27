package com.smartlamp.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 中文分词器（零外部依赖）：领域词典最大正向匹配 + 未命中中文重叠二字切分 + ASCII/数字整词。
// 词典由知识库 keywords 与领域补充词表构成，天然贴合业务领域；
// 后续升级向量检索时只需替换本类，索引构建方式不变。
public class Segmenter {

    // 词典最长匹配窗口（领域词一般不超过 8 字）
    private static final int MAX_WORD_LEN = 8;

    private final Set<String> dictionary;

    public Segmenter(Set<String> dictionary) {
        this.dictionary = dictionary;
    }

    // 分词规则：
    //  - 词典最长匹配优先（如"确认告警"优先于"确认"+"告警"）
    //  - 未命中的连续中文按重叠二字切分（单字无区分度，不单独成词）
    //  - 英文/数字/下划线按非单词字符边界切为整词（lamp001、COMMAND_ACCEPTED 不碎）
    //  - 标点与空白跳过
    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) return tokens;

        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (isWordChar(c)) {
                int start = i;
                while (i < n && isWordChar(text.charAt(i))) i++;
                tokens.add(text.substring(start, i));
                continue;
            }
            if (isCjk(c)) {
                boolean matched = false;
                for (int len = Math.min(MAX_WORD_LEN, n - i); len >= 2; len--) {
                    String word = text.substring(i, i + len);
                    if (dictionary.contains(word)) {
                        tokens.add(word);
                        i += len;
                        matched = true;
                        break;
                    }
                }
                if (matched) continue;
                if (i + 1 < n && isCjk(text.charAt(i + 1))) {
                    tokens.add(text.substring(i, i + 2));
                    i += 1; // 重叠前进，保证连续中文逐个二字覆盖
                } else {
                    i += 1; // 孤立单字跳过
                }
                continue;
            }
            i++; // 标点/空白跳过
        }
        return tokens;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isCjk(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }
}
