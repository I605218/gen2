package com.codeassistant.backend.dto.agent;

public record AgentStageTrace(
        String stage,
        String framework,
        String status,
        int attempt,
        long durationMs,
        String summary
) {
}
