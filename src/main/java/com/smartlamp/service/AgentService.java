package com.smartlamp.service;

import com.smartlamp.agent.AgentActionExecutor;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.SourceItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentService {

    @Autowired
    private AgentActionExecutor actionExecutor;

    public AskResponse ask(String question) {
        // 简单意图识别（后续替换为真实 Agent）
        String upper = question.toUpperCase();
        if (upper.contains("打开") && upper.contains("SL-")) {
            String deviceId = extractDeviceId(upper);
            if (deviceId != null) {
                String commandId = actionExecutor.executeControl(deviceId, "ON");
                String answer = "已下发开灯指令，命令ID: " + commandId;
                SourceItem source = new SourceItem("系统设备数据", "system_data", 1.0);
                return new AskResponse(answer, Arrays.asList(source));
            }
        } else if (upper.contains("关闭") && upper.contains("SL-")) {
            String deviceId = extractDeviceId(upper);
            if (deviceId != null) {
                String commandId = actionExecutor.executeControl(deviceId, "OFF");
                String answer = "已下发关灯指令，命令ID: " + commandId;
                SourceItem source = new SourceItem("系统设备数据", "system_data", 1.0);
                return new AskResponse(answer, Arrays.asList(source));
            }
        }

        // 默认回答（保留原桩）
        String answer = "常见原因：1. 供电异常；2. 通信模块故障；3. 传感器损坏；4. 网关离线。请先检查供电和网络。";
        SourceItem source = new SourceItem("路灯常见故障排查手册", "knowledge", 0.92);
        return new AskResponse(answer, Arrays.asList(source));
    }

    private String extractDeviceId(String text) {
        Pattern pattern = Pattern.compile("SL-\\d+");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}