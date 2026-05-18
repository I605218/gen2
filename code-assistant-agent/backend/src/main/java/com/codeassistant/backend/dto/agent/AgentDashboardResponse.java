package com.codeassistant.backend.dto.agent;

import java.util.List;

public record AgentDashboardResponse(
        List<AgentHistoryItem> recentHistory,
        AgentStats stats,
        List<String> quickPrompts
) {
}
