package com.smartlamp.controller;

import com.smartlamp.agent.actions.ActionService;
import com.smartlamp.agent.actions.AgentAction;
import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.AskRequest;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.service.AgentConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private AgentConversationService chatService;

    @Autowired
    private ActionService actionService;

    // POST /api/agent/ask（前端聊天入口；Agent V3 起支持可选 conversationId，为空自动新建会话；
    // requestId 可选——前端超时重试携带同一 requestId 时不重复保存消息）
    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(@RequestBody AskRequest request) {
        AskResponse response = chatService.chat(
                request.getQuestion(), request.getConversationId(), currentUser(), request.getRequestId());
        return ApiResponse.success(response);
    }

    // POST /api/agent/actions/{actionId}/confirm（用户确认待确认操作：归属/角色校验 + 二次校验通过后执行）
    @PostMapping("/actions/{actionId}/confirm")
    public ApiResponse<AgentAction> confirmAction(@PathVariable String actionId) {
        return ApiResponse.success(actionService.confirmAndExecute(actionId, currentUser(), currentRole()));
    }

    // POST /api/agent/actions/{actionId}/cancel（用户取消待确认操作）
    @PostMapping("/actions/{actionId}/cancel")
    public ApiResponse<AgentAction> cancelAction(@PathVariable String actionId) {
        return ApiResponse.success(actionService.cancel(actionId, currentUser(), currentRole()));
    }

    // 当前登录用户（JwtAuthenticationFilter 已设置认证上下文）
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null || auth.getName() == null) ? "unknown" : auth.getName();
    }

    // 当前角色（安全上下文 authority 形如 ROLE_admin / ROLE_operator / ROLE_municipal）
    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return null;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }
}
