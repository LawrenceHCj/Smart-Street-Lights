package com.smartlamp.controller;

import com.smartlamp.dto.SummaryDTO;
import com.smartlamp.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
public class SseController {

    @Autowired
    private DashboardService dashboardService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @GetMapping("/events")
    public SseEmitter handleEvents() {
        SseEmitter emitter = new SseEmitter(0L); // 不超时

        // 立即推送一次
        sendSummary(emitter);

        // 每 5 秒推送一次摘要
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendSummary(emitter);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, 5, 5, TimeUnit.SECONDS);

        // 设置回调清理
        emitter.onCompletion(() -> System.out.println("SSE 连接完成"));
        emitter.onTimeout(() -> System.out.println("SSE 连接超时"));
        emitter.onError((e) -> System.out.println("SSE 连接错误: " + e.getMessage()));

        return emitter;
    }

    private void sendSummary(SseEmitter emitter) {
        try {
            SummaryDTO summary = dashboardService.getSummary();
            // SSE 事件格式：data: JSON\n\n
            emitter.send(SseEmitter.event()
                    .name("summary")
                    .data(summary));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}