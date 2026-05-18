package com.codeassistant.backend.dto.agent;

import com.codeassistant.backend.model.agent.AgentTaskType;

public record AgentParseResult(
        AgentTaskType taskType,
        String question,
        String language,
        String errorMessage,
        String knowledgePoint,
        Integer practiceCount
) {
}
