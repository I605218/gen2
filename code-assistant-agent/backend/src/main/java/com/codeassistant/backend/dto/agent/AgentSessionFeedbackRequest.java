package com.codeassistant.backend.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentSessionFeedbackRequest(
        String sessionId,
        @Size(max = 1000, message = "feedback 长度不能超过 1000")
        String feedback
) {
}
