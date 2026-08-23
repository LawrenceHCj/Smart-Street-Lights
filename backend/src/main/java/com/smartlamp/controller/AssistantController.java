package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.AskRequest;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.service.AgentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Backwards-compatible endpoint retained for callers of the Node integration service. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AgentService agentService;

    public AssistantController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ApiResponse<AskResponse> chat(@RequestBody AskRequest request) {
        return ApiResponse.success(agentService.ask(request.getQuestion()));
    }
}
