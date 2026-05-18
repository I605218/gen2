package com.codeassistant.backend.controller;

import com.codeassistant.backend.dto.agent.AgentConversationMessageItem;
import com.codeassistant.backend.dto.agent.AgentConversationSessionItem;
import com.codeassistant.backend.dto.agent.AgentConversationSessionRequest;
import com.codeassistant.backend.dto.agent.AgentConversationThreadResponse;
import com.codeassistant.backend.dto.agent.AgentConversationTurnRequest;
import com.codeassistant.backend.dto.auth.AuthSessionUser;
import com.codeassistant.backend.service.agent.ConversationContextService;
import com.codeassistant.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationContextService conversationContextService;
    private final AuthService authService;

    public ConversationController(ConversationContextService conversationContextService, AuthService authService) {
        this.conversationContextService = conversationContextService;
        this.authService = authService;
    }

    @GetMapping
    public List<AgentConversationSessionItem> list(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                   @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        return conversationContextService.listSessions(user != null ? user.id() : null, sessionId);
    }

    @GetMapping("/messages")
    public List<AgentConversationMessageItem> messages(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                       @RequestParam String sessionId) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        return conversationContextService.listMessages(user != null ? user.id() : null, sessionId);
    }

    @PostMapping
    public AgentConversationSessionItem create(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                               @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                                               @Valid @RequestBody AgentConversationSessionRequest request) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        String resolvedSessionId = request.sessionId() != null && !request.sessionId().isBlank() ? request.sessionId() : sessionId;
        return conversationContextService.createSession(user != null ? user.id() : null, resolvedSessionId, request.title());
    }

    @PostMapping("/rename")
    public AgentConversationSessionItem rename(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                               @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                                               @Valid @RequestBody AgentConversationSessionRequest request) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        return conversationContextService.renameSession(user != null ? user.id() : null, sessionId, request.sessionId(), request.title());
    }

    @PostMapping("/chat")
    public AgentConversationThreadResponse chat(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                @Valid @RequestBody AgentConversationTurnRequest request) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        return conversationContextService.chat(user != null ? user.id() : null, request);
    }

    @DeleteMapping
    public boolean delete(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                          @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                          @RequestParam("sessionId") String sessionIdParam) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        return conversationContextService.deleteSession(user != null ? user.id() : null, sessionId, sessionIdParam);
    }
}
