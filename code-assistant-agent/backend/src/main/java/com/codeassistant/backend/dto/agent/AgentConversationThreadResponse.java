package com.codeassistant.backend.dto.agent;

import java.util.List;

public record AgentConversationThreadResponse(
        AgentConversationSessionItem conversation,
        List<AgentConversationMessageItem> messages,
        AgentResponse response
) {
}
