package com.codeassistant.backend.controller;

import com.codeassistant.backend.dto.agent.AgentDashboardResponse;
import com.codeassistant.backend.dto.agent.AgentHistoryItem;
import com.codeassistant.backend.dto.agent.AgentSessionFeedbackRequest;
import com.codeassistant.backend.dto.agent.AgentSessionRequest;
import com.codeassistant.backend.dto.agent.AgentSessionStateResponse;
import com.codeassistant.backend.dto.auth.AuthSessionUser;
import com.codeassistant.backend.service.agent.AgentWorkspaceService;
import com.codeassistant.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class AgentWorkspaceController {

    private final AgentWorkspaceService agentWorkspaceService;
    private final AuthService authService;

    public AgentWorkspaceController(AgentWorkspaceService agentWorkspaceService,
                                    AuthService authService) {
        this.agentWorkspaceService = agentWorkspaceService;
        this.authService = authService;
    }

    @GetMapping("/dashboard")
    public AgentDashboardResponse dashboard() {
        return agentWorkspaceService.getDashboard();
    }

    @PostMapping("/history")
    public List<AgentHistoryItem> sessionHistory(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                 @Valid @RequestBody AgentSessionRequest request) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        return agentWorkspaceService.getHistory(user != null ? user.id() : null, request.sessionId());
    }

    @GetMapping("/session-state")
    public AgentSessionStateResponse sessionState(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                  @RequestParam String sessionId) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        return agentWorkspaceService.getSessionState(user != null ? user.id() : null, sessionId);
    }

    @PostMapping("/history/{id}/feedback")
    public void saveFeedback(@PathVariable Long id, @Valid @RequestBody AgentSessionFeedbackRequest request) {
        agentWorkspaceService.saveFeedback(id, request.feedback());
    }

    @PostMapping("/history/{id}/pin")
    public void togglePin(@PathVariable Long id, @RequestParam boolean pinned) {
        agentWorkspaceService.togglePinned(id, pinned);
    }
}
