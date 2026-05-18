package com.codeassistant.backend.controller;

import com.codeassistant.backend.dto.agent.AgentAutoRequest;
import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.dto.agent.AgentResponse;
import com.codeassistant.backend.dto.auth.AuthSessionUser;
import com.codeassistant.backend.service.agent.AgentRequestParsingService;
import com.codeassistant.backend.service.agent.AgentWorkspaceService;
import com.codeassistant.backend.service.agent.CodeAssistantAgentService;
import com.codeassistant.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class CodeAssistantAgentController {

    private static final String STRUCTURED_REQUEST = "structured-request";
    private static final String NATURAL_LANGUAGE_REQUEST = "natural-language-request";
    private static final String DEFAULT_SESSION = "guest-session";

    private final CodeAssistantAgentService codeAssistantAgentService;
    private final AgentRequestParsingService agentRequestParsingService;
    private final AgentWorkspaceService agentWorkspaceService;
    private final AuthService authService;

    public CodeAssistantAgentController(CodeAssistantAgentService codeAssistantAgentService,
                                        AgentRequestParsingService agentRequestParsingService,
                                        AgentWorkspaceService agentWorkspaceService,
                                        AuthService authService) {
        this.codeAssistantAgentService = codeAssistantAgentService;
        this.agentRequestParsingService = agentRequestParsingService;
        this.agentWorkspaceService = agentWorkspaceService;
        this.authService = authService;
    }

    @PostMapping("/execute")
    public AgentResponse execute(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                 @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                                 @Valid @RequestBody AgentRequest request) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        AgentResponse response = codeAssistantAgentService.execute(request);
        agentWorkspaceService.saveExecution(user != null ? user.id() : null, resolveSessionId(sessionId), STRUCTURED_REQUEST, request.question(), request.code(), response);
        return response;
    }

    @PostMapping("/auto-execute")
    public AgentResponse autoExecute(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                     @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                                     @Valid @RequestBody AgentAutoRequest request) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        AgentRequest parsedRequest = agentRequestParsingService.parse(request);
        AgentResponse response = codeAssistantAgentService.execute(parsedRequest);
        agentWorkspaceService.saveExecution(user != null ? user.id() : null, resolveSessionId(sessionId), NATURAL_LANGUAGE_REQUEST, request.message(), request.code(), response);
        return response;
    }

    private String resolveSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : DEFAULT_SESSION;
    }
}
