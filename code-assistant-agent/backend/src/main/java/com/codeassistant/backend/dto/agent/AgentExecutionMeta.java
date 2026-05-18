package com.codeassistant.backend.dto.agent;

import java.util.List;

public record AgentExecutionMeta(
        String executionId,
        int planStepCount,
        int toolCallCount,
        int actionTraceCount,
        boolean reflexionTriggered,
        int retryCount,
        long totalDurationMs,
        List<AgentMemoryEntry> memory,
        List<AgentStageTrace> stageTraces
) {
}
