package com.smartlamp.controller;

import com.smartlamp.service.SseHub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {

    @Autowired
    private SseHub sseHub;

    @GetMapping("/events")
    public SseEmitter handleEvents() {
        return sseHub.register();
    }
}