package com.smartlamp.dto;

import lombok.Data;

import java.util.List;

@Data
public class AskResponse {
    private String answer;
    private List<SourceItem> sources;

    // 会话标识（Agent V3）：新建会话时返回，前端后续请求回传实现多轮记忆
    private String conversationId;

    // 待确认操作（阶段21 联调落地）：产生待确认操作时返回，前端据此在对话中渲染确认按钮；
    // 普通问答/自动执行的操作（开灯关灯）不返回
    private PendingActionInfo action;

    public AskResponse(String answer, List<SourceItem> sources) {
        this.answer = answer;
        this.sources = sources;
    }

    public AskResponse(String answer, List<SourceItem> sources, String conversationId) {
        this.answer = answer;
        this.sources = sources;
        this.conversationId = conversationId;
    }
}