package com.codeassistant.backend.dto.agent;

import java.time.LocalDateTime;

public record AgentConversationSessionItem(
        Long id,
        String sessionId,
        String title,
        String summary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long messageCount,
        String latestTaskType,
        String latestPrompt,
        String latestAnswer,
        boolean pinned
) {
}
