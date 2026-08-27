package com.smartlamp.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/** Streams database-backed summaries to clients; it replaces the Node process-local SSE hub. */
@Service
public class SseHub {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final SummaryService summaryService;

    public SseHub(SummaryService summaryService) { this.summaryService = summaryService; }

    public synchronized SseEmitter subscribe() {
        if (emitters.size() >= 50) throw new IllegalStateException("SSE 连接数已达上限");
        SseEmitter emitter = new SseEmitter(60_000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter);
        return emitter;
    }

    @Scheduled(fixedDelay = 5000)
    public void broadcast() {
        emitters.forEach(this::send);
    }

    private void send(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("summary").data(summaryService.getSummary()));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
        }
    }
}
