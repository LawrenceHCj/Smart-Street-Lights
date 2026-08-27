package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.service.SseHub;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventController {
    private final SseHub sseHub;
    public EventController(SseHub sseHub) { this.sseHub = sseHub; }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() { return sseHub.subscribe(); }

    /** 连接数超限时返回 429 而非 500。 */
    @ExceptionHandler(SseHub.TooManyConnectionsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> onTooManyConnections(SseHub.TooManyConnectionsException ex) {
        return ApiResponse.error(429, ex.getMessage());
    }
}
