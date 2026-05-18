package com.codeassistant.backend.dto.agent;

public record AgentSessionStateResponse(
        String sessionId,
        long messageCount,
        String latestTaskType,
        String latestPrompt,
        String latestAnswer,
        boolean hasPinnedRecord
) {
}
