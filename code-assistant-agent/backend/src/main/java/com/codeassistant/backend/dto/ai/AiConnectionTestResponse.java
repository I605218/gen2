package com.codeassistant.backend.dto.ai;

public record AiConnectionTestResponse(
        boolean success,
        String model,
        String message,
        String replyPreview
) {
}
