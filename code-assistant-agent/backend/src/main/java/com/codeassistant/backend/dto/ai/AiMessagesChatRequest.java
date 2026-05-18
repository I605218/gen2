package com.codeassistant.backend.dto.ai;

import java.util.List;

public record AiMessagesChatRequest(
        String model,
        List<OpenAiMessage> messages,
        Double temperature,
        Integer max_tokens
) {
}
