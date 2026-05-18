package com.codeassistant.backend.model.agent;

public record AgentToolResult(
        String toolName,
        String toolInput,
        String toolOutput
) {
}
