package com.codeassistant.backend.service.agent;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.dto.agent.AgentResponse;

public interface CodeAssistantAgentService {

    AgentResponse execute(AgentRequest request);
}
