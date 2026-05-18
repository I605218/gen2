package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;

public interface AgentTool {

    String name();

    boolean supports(AgentTaskType taskType);

    AgentToolResult execute(AgentRequest request);
}
