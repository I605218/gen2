package com.codeassistant.backend.service.ai;

import com.codeassistant.backend.dto.ai.AiChatResponse;
import com.codeassistant.backend.dto.ai.AiConnectionTestResponse;
import com.codeassistant.backend.dto.ai.OpenAiMessage;

import java.util.List;

public interface AiChatService {

    AiChatResponse chat(String message, String systemPrompt);

    AiChatResponse chatWithMessages(List<OpenAiMessage> messages, Double temperature, Integer maxTokens);

    AiConnectionTestResponse testConnection();
}
