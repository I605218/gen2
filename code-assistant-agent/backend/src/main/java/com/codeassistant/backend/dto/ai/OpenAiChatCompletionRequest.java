package com.codeassistant.backend.dto.ai;

import java.util.List;

public record OpenAiChatCompletionRequest(
        String model,
        List<OpenAiMessage> messages,
        Double temperature,
        Integer max_tokens
) {
}
