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

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private static final int MAX_CONNECTIONS = 50;

    public SseEmitter register() {
        if (emitters.size() >= MAX_CONNECTIONS) {
            throw new IllegalStateException("SSE 连接数已达上限");
        }
        SseEmitter emitter = new SseEmitter(60_000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));
        return emitter;
    }

    // 兼容旧调用：subscribe 等同于 register
    public SseEmitter subscribe() {
        return register();
    }

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