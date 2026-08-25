package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.AskRequest;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.service.AgentConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private AgentConversationService chatService;

    // POST /api/agent/ask（前端聊天入口；Agent V3 起支持可选 conversationId，为空自动新建会话）
    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(@RequestBody AskRequest request) {
        AskResponse response = chatService.chat(request.getQuestion(), request.getConversationId(), currentUser());
        return ApiResponse.success(response);
    }

    // 当前登录用户（JwtAuthenticationFilter 已设置认证上下文）
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null || auth.getName() == null) ? "unknown" : auth.getName();
    }
}
