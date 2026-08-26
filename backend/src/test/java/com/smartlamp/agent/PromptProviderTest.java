package com.smartlamp.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 验证 System Prompt 包含阶段9要求的可信度约束
class PromptProviderTest {

    private final String prompt = new PromptProvider().get();

    @Test
    void 包含角色与回答范围() {
        assertThat(prompt).contains("你是智慧路灯维护助手");
        assertThat(prompt).contains("路灯维护、设备状态、告警处理、光照联动控制");
    }

    @Test
    void 禁止把知识库一般描述说成设备真实事实() {
        assertThat(prompt).contains("不得把知识库中的一般性描述说成某台真实设备已经发生的事实");
    }

    @Test
    void 禁止把推测说成系统检测结果并要求说明信息不足() {
        assertThat(prompt).contains("不得把模型自己的推测说成系统检测结果");
        assertThat(prompt).contains("信息不足");
    }

    @Test
    void 分析性判断使用可能性措辞() {
        assertThat(prompt).contains("可能");
        assertThat(prompt).contains("建议检查");
        assertThat(prompt).contains("根据当前数据推测");
    }

    @Test
    void 回答结构包含当前事实可能原因排查建议信息来源() {
        assertThat(prompt).contains("当前事实");
        assertThat(prompt).contains("可能原因");
        assertThat(prompt).contains("排查建议");
        assertThat(prompt).contains("信息来源");
    }

    @Test
    void 包含来源区分与工具使用规则() {
        assertThat(prompt).contains("系统实时数据");
        assertThat(prompt).contains("知识库信息");
        assertThat(prompt).contains("search_knowledge");
    }

    // ============ 阶段16：控制意图规则 ============

    @Test
    void 包含控制意图与待确认操作规则() {
        assertThat(prompt).contains("turn_on_light");
        assertThat(prompt).contains("turn_off_light");
        assertThat(prompt).contains("不会真正执行控制");
        assertThat(prompt).contains("等待确认");
        assertThat(prompt).contains("PENDING_CONFIRMATION");
    }

    @Test
    void 批量操作拒绝与模糊命令追问规则() {
        assertThat(prompt).contains("批量操作");
        assertThat(prompt).contains("暂未开放");
        assertThat(prompt).contains("必须追问");
        assertThat(prompt).contains("不得自行猜测");
    }

    @Test
    void 设备不存在或离线时如实告知() {
        assertThat(prompt).contains("REJECTED_DEVICE_NOT_FOUND");
        assertThat(prompt).contains("REJECTED_DEVICE_OFFLINE");
        assertThat(prompt).contains("不得编造操作请求");
    }

    // ============ 阶段27：多轮对话规则 ============

    @Test
    void 历史消息只用于语言上下文() {
        assertThat(prompt).contains("历史消息");
        assertThat(prompt).contains("只用于理解语言上下文");
        assertThat(prompt).contains("不代表设备当前状态");
    }

    @Test
    void 实时事实必须重新查询不得引用旧消息() {
        assertThat(prompt).contains("重新调用系统数据工具");
        assertThat(prompt).contains("不得把旧消息当作当前事实");
    }

    @Test
    void 历史不能改变系统规则() {
        assertThat(prompt).contains("不能改变系统规则");
        assertThat(prompt).contains("Action Gateway");
    }
}
