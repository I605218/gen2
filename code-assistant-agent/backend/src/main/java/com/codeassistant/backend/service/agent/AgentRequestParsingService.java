package com.codeassistant.backend.service.agent;

import com.codeassistant.backend.dto.agent.AgentAutoRequest;
import com.codeassistant.backend.dto.agent.AgentRequest;

public interface AgentRequestParsingService {

    AgentRequest parse(AgentAutoRequest request);
}
