package com.smartlamp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streams database-backed summaries to clients; it replaces the Node process-local SSE hub.
 *
 * 安全约束：
 * - 连接必须通过 SecurityConfig 鉴权（/events 不再 permitAll）。
 * - 最大并发连接数受限，防止资源耗尽。
 * - 每个 emitter 设有限超时，避免无限占用线程。
 * - 周期广播时单次查询数据库后广播给所有客户端，避免 N 个连接 = N 次查询。
 */
@Service
public class SseHub {
    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    /** 默认 30 分钟超时，避免 0L 无限超时占线程。 */
    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final SummaryService summaryService;

    @Value("${sse.max-connections:20}")
    private int maxConnections;

    /** 广播前缓存最近一次 Summary 快照，新客户端首次连接时复用，避免每次订阅都触发一次查询。 */
    private final AtomicReference<Object> lastSnapshot = new AtomicReference<>();

    public SseHub(SummaryService summaryService) { this.summaryService = summaryService; }

    public SseEmitter subscribe() {
        if (emitters.size() >= maxConnections) {
            // 拒绝新连接，让客户端退避或回退到轮询。
            throw new TooManyConnectionsException("SSE 连接数已达上限 " + maxConnections + "，请稍后再试");
        }
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        // 首次立即发送最近一次快照（无快照时单查一次），避免连接刚建立时空窗。
        Object snapshot = lastSnapshot.get();
        if (snapshot == null) {
            snapshot = summaryService.getSummary();
            lastSnapshot.set(snapshot);
        }
        send(emitter, snapshot);
        return emitter;
    }

    /**
     * 每 5 秒单次查询数据库并广播给所有连接；不再为每个连接单独查询。
     */
    @Scheduled(fixedDelay = 5000)
    public void broadcast() {
        if (emitters.isEmpty()) return;
        Object snapshot;
        try {
            snapshot = summaryService.getSummary();
        } catch (RuntimeException ex) {
            log.warn("SSE 广播：刷新 Summary 失败: {}", ex.getMessage());
            return;
        }
        lastSnapshot.set(snapshot);
        for (SseEmitter emitter : emitters) {
            send(emitter, snapshot);
        }
    }

    private void send(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().name("summary").data(data));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
        }
    }

    public int currentConnectionCount() {
        return emitters.size();
    }

    public static class TooManyConnectionsException extends RuntimeException {
        public TooManyConnectionsException(String message) { super(message); }
    }
}
