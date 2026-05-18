package com.codeassistant.backend.dto.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentConversationSessionRequest(
        @NotBlank(message = "sessionId 不能为空")
        String sessionId,
        String title
) {
}
