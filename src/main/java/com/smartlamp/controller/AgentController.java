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

    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(@RequestBody AskRequest request) {
        AskResponse response = chatService.chat(request.getQuestion(), request.getConversationId(), currentUser());
        return ApiResponse.success(response);
    }

    @PostMapping("/actions/{actionId}/confirm")
    public ApiResponse<AgentAction> confirmAction(@PathVariable String actionId) {
        // 角色校验
        if (!isAdminOrOperator()) {
            return ApiResponse.error(403, "权限不足");
        }
        String username = currentUser();
        AgentAction action = actionService.confirmAndExecute(actionId, username);
        return action == null
                ? ApiResponse.error(400, "无权操作或操作不存在")
                : ApiResponse.success(action);
    }

    @PostMapping("/actions/{actionId}/cancel")
    public ApiResponse<AgentAction> cancelAction(@PathVariable String actionId) {
        if (!isAdminOrOperator()) {
            return ApiResponse.error(403, "权限不足");
        }
        String username = currentUser();
        AgentAction action = actionService.cancel(actionId, username);
        return action == null
                ? ApiResponse.error(400, "无权操作或操作不存在")
                : ApiResponse.success(action);
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null || auth.getName() == null) ? "unknown" : auth.getName();
    }

    private boolean isAdminOrOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if (role.equals("ROLE_ADMIN") || role.equals("ROLE_OPERATOR")) {
                return true;
            }
        }
        return false;
    }
}