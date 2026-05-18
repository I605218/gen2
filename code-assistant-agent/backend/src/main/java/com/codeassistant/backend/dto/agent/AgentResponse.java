package com.codeassistant.backend.dto.agent;

import java.util.List;

public record AgentResponse(
        String taskType,
        List<String> frameworks,
        AgentExecutionMeta execution,
        List<AgentPlanStep> planSteps,
        List<String> reasoningSteps,
        List<AgentActionTrace> actionTraces,
        List<AgentToolTrace> tools,
        String reflection,
        String answer
) {
}
