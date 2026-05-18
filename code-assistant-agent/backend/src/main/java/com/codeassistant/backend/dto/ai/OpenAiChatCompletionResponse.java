package com.codeassistant.backend.dto.ai;

import java.util.List;

public record OpenAiChatCompletionResponse(
        String model,
        List<Choice> choices
) {
    public record Choice(
            Integer index,
            OpenAiMessage message,
            String finishReason
    ) {
    }
}
