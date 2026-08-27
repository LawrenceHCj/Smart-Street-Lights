package com.smartlamp.controller;

import com.smartlamp.agent.conversation.AgentConversation;
import com.smartlamp.agent.conversation.AgentMessage;
import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.AskRequest;
import com.smartlamp.dto.AskResponse;
import com.smartlamp.dto.ConversationDTO;
import com.smartlamp.service.AgentConversationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 智能体聊天入口（Agent V3）：向后兼容旧调用，新增会话创建与历史读取。 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AgentConversationService chatService;

    public AssistantController(AgentConversationService chatService) {
        this.chatService = chatService;
    }

    // POST /api/assistant/chat（兼容旧调用：只传 question 即可，conversationId 为空时自动新建会话；
    // requestId 可选——前端超时重试携带同一 requestId 时不重复保存消息）
    @PostMapping("/chat")
    public ApiResponse<AskResponse> chat(@RequestBody AskRequest request) {
        return ApiResponse.success(chatService.chat(
                request.getQuestion(), request.getConversationId(), currentUser(), request.getRequestId()));
    }

    // POST /api/assistant/conversations 创建新会话（可带首条问题作标题）
    @PostMapping("/conversations")
    public ApiResponse<AgentConversation> createConversation(@RequestBody(required = false) AskRequest request) {
        String question = request == null ? null : request.getQuestion();
        return ApiResponse.success(chatService.createConversation(currentUser(), question));
    }

    // GET /api/assistant/conversations 当前用户的会话列表（按最近更新倒序）
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationDTO>> listConversations() {
        return ApiResponse.success(chatService.listConversations(currentUser()));
    }

    // GET /api/assistant/conversations/{conversationId} 读取单个会话详情
    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationDTO> getConversation(@PathVariable String conversationId) {
        return ApiResponse.success(chatService.getConversationDetail(conversationId, currentUser()));
    }

    // GET /api/assistant/conversations/{conversationId}/messages 读取会话历史（按时间升序；
    // offset/limit 可选，服务端分页，单页上限 200——阶段修复#8）
    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<AgentMessage>> messages(@PathVariable String conversationId,
                                                    @RequestParam(defaultValue = "0") int offset,
                                                    @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.success(chatService.getMessages(conversationId, currentUser(), offset, limit));
    }

    // DELETE /api/assistant/conversations/{conversationId} 删除会话及其全部历史消息
    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(@PathVariable String conversationId) {
        chatService.deleteConversation(conversationId, currentUser());
        return ApiResponse.success(null);
    }

    // 当前登录用户（JwtAuthenticationFilter 已设置认证上下文）
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null || auth.getName() == null) ? "unknown" : auth.getName();
    }
}
