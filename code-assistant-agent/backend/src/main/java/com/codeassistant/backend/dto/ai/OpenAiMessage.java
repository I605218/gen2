package com.codeassistant.backend.dto.ai;

public record OpenAiMessage(
        String role,
        String content
) {
}
