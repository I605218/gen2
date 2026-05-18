package com.codeassistant.backend.dto.agent;

public record AgentPlanStep(
        int stepNumber,
        String goal,
        String framework,
        String status
) {
}
