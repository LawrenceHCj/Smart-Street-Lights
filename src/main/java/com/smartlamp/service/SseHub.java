package com.smartlamp.service;

import com.smartlamp.dto.SummaryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseHub {

    @Autowired
    private DashboardService dashboardService;

    // 线程安全的客户端列表
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // 最大连接数
    private static final int MAX_CONNECTIONS = 50;

    /**
     * 注册一个 SSE 客户端
     */
    public SseEmitter register() {
        if (emitters.size() >= MAX_CONNECTIONS) {
            throw new IllegalStateException("SSE 连接数已达上限");
        }
        SseEmitter emitter = new SseEmitter(60_000L); // 60 秒超时
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));
        return emitter;
    }

    /**
     * 每 5 秒计算一次汇总，并广播给所有客户端
     */
    @Scheduled(fixedRate = 5000)
    public void broadcast() {
        if (emitters.isEmpty()) {
            return;
        }
        SummaryDTO summary = dashboardService.getSummary();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("summary")
                        .data(summary));
            } catch (IOException e) {
                emitter.completeWithError(e);
                emitters.remove(emitter);
            }
        }
    }
}