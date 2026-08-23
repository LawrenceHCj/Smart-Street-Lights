package com.smartlamp.service;

import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.SourceItem;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class AgentService {

    public AskResponse ask(String question) {
        // 暂时返回固定回答，后续接入 RAG 和 AI 智能体
        String answer = "常见原因：1. 供电异常；2. 通信模块故障；3. 传感器损坏；4. 网关离线。请先检查供电和网络。";
        SourceItem source = new SourceItem("路灯常见故障排查手册", "第 3 节", 0.92);
        return new AskResponse(answer, Arrays.asList(source));
    }
}