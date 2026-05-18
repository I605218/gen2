package com.codeassistant.backend.dto.agent;

import java.util.List;

public record AgentStats(
        long totalSessions,
        long totalRequests,
        long errorExplanationCount,
        long algorithmGuideCount,
        long practiceGenerationCount,
        List<String> hotTaskTypes
) {
}
