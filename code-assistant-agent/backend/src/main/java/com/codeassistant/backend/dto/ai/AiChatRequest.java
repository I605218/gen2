package com.codeassistant.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
        @NotBlank(message = "message 不能为空")
        String message,
        String systemPrompt
) {
}
