package com.smartlamp.agent;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrieverTest {

    private Retriever newRetriever() {
        Retriever retriever = new Retriever();
        ReflectionTestUtils.setField(retriever, "knowledgeBase", new KnowledgeBase());
        return retriever;
    }

    @Test
    void 命中问题返回匹配条目及完整字段() {
        List<Retriever.KbMatch> results = newRetriever().retrieve("路灯离线应该怎么排查？");

        // 设备离线排查 与 设备状态异常 各命中 1 词，同分必须保持知识库原始顺序（稳定排序）
        assertThat(results).hasSize(2);
        KnowledgeEntry top = results.get(0).entry();
        assertThat(top.getId()).isEqualTo("kb-offline-troubleshooting");
        assertThat(top.getTitle()).isEqualTo("设备离线排查");
        assertThat(top.getCategory()).isEqualTo("告警处理");
        assertThat(top.getContent()).isNotBlank();
        assertThat(top.getKeywords()).isNotEmpty();
        assertThat(top.getSource()).isEqualTo("内部知识库");
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(1);
        assertThat(results.get(1).entry().getTitle()).isEqualTo("设备状态异常");
    }

    @Test
    void 多关键词命中评分更高并排在前面() {
        // "手动控制告警"：手动控制回执命中2词，设备离线排查与告警处理流程各命中1词
        List<Retriever.KbMatch> results = newRetriever().retrieve("手动控制告警");

        assertThat(results.get(0).entry().getTitle()).isEqualTo("手动控制回执");
        assertThat(results.get(1).entry().getTitle()).isEqualTo("设备离线排查");
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    }

    @Test
    void limit生效() {
        List<Retriever.KbMatch> results = newRetriever().retrieve("手动控制告警", 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).entry().getTitle()).isEqualTo("手动控制回执");
    }

    @Test
    void 无命中返回空列表() {
        assertThat(newRetriever().retrieve("今天天气怎么样？")).isEmpty();
    }

    @Test
    void 空问题返回空列表() {
        Retriever retriever = newRetriever();
        assertThat(retriever.retrieve("   ")).isEmpty();
        assertThat(retriever.retrieve(null)).isEmpty();
    }

    // ============ 智能体使用类条目（知识库更新：覆盖智能助手问答） ============

    @Test
    void 口语化开灯请求命中智能助手控制流程() {
        List<Retriever.KbMatch> results = newRetriever().retrieve("帮我打开路灯");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).entry().getId()).isEqualTo("kb-agent-control-flow");
    }

    @Test
    void 确认后灯没亮命中控制结果状态说明() {
        List<Retriever.KbMatch> results = newRetriever().retrieve("确认后灯没亮");

        assertThat(results).extracting(m -> m.entry().getId())
                .contains("kb-agent-result-status");
    }

    @Test
    void 批量关灯问题命中能力边界条目() {
        List<Retriever.KbMatch> results = newRetriever().retrieve("你能批量关灯吗", 5);

        assertThat(results).extracting(m -> m.entry().getId())
                .contains("kb-agent-capability-boundary");
    }

    @Test
    void 智能助手能做什么命中能力边界() {
        List<Retriever.KbMatch> results = newRetriever().retrieve("智能助手能做什么");

        assertThat(results).extracting(m -> m.entry().getId())
                .contains("kb-agent-capability-boundary");
    }

    @Test
    void 供电数据采集条目已修订支持电压电流() {
        List<Retriever.KbMatch> results = newRetriever().retrieve("设备电压电流", 5);

        KnowledgeEntry entry = results.stream().map(Retriever.KbMatch::entry)
                .filter(e -> "kb-power-abnormal".equals(e.getId())).findFirst().orElseThrow();
        assertThat(entry.getContent()).contains("voltage").doesNotContain("未采集");
    }
}
