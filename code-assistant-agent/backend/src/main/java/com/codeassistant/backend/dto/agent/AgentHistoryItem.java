package com.codeassistant.backend.dto.agent;

import java.time.LocalDateTime;

public record AgentHistoryItem(
        Long id,
        String sessionId,
        String requestType,
        String taskType,
        String userPrompt,
        String code,
        String responseSummary,
        String answer,
        AgentResponse responsePayload,
        String feedback,
        boolean pinned,
        LocalDateTime createdAt
) {
}
