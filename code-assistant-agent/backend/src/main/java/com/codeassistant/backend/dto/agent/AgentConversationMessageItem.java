package com.codeassistant.backend.dto.agent;

import java.time.LocalDateTime;

public record AgentConversationMessageItem(
        Long id,
        Long conversationId,
        String sessionId,
        String role,
        Integer turnIndex,
        String content,
        String codeContent,
        String taskType,
        LocalDateTime createdAt
) {
}
