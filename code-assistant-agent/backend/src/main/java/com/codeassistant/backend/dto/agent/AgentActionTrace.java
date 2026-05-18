package com.codeassistant.backend.dto.agent;

public record AgentActionTrace(
        String thought,
        String action,
        String observation
) {
}
