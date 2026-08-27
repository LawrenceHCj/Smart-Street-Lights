package com.smartlamp.controller;

import com.smartlamp.service.SseHub;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventController {
    private final SseHub sseHub;
    public EventController(SseHub sseHub) { this.sseHub = sseHub; }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() { return sseHub.subscribe(); }
}
