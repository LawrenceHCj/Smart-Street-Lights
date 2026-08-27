package com.smartlamp.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 分词器单测：词典最长匹配 / 未命中中文重叠二字切分 / ASCII 整词
class SegmenterTest {

    private final Segmenter segmenter = new Segmenter(Set.of(
            "离线", "确认", "确认告警", "打开", "路灯", "闪烁", "COMMAND_ACCEPTED"));

    @Test
    void 词典最长匹配优先() {
        assertThat(segmenter.tokenize("确认告警")).containsExactly("确认告警");
    }

    @Test
    void 未命中中文按重叠二字切分() {
        assertThat(segmenter.tokenize("今天天气")).containsExactly("今天", "天天", "天气");
    }

    @Test
    void 英文字母数字下划线整词切分() {
        assertThat(segmenter.tokenize("COMMAND_ACCEPTED lamp001 120"))
                .containsExactly("COMMAND_ACCEPTED", "lamp001", "120");
    }

    @Test
    void 混合文本词典与整词同时生效() {
        assertThat(segmenter.tokenize("设备 lamp001 离线了"))
                .containsExactly("设备", "lamp001", "离线");
    }

    @Test
    void 标点跳过孤立单字不成词() {
        assertThat(segmenter.tokenize("！？ lamp001，。")).containsExactly("lamp001");
    }
}
