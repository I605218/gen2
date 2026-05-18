package com.codeassistant.backend.dto.agent;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AgentConversationTurnRequest(
        @NotBlank(message = "sessionId 不能为空")
        String sessionId,
        @NotBlank(message = "message 不能为空")
        String message,
        String code,
        String summary,
        List<String> selectedTools,
        Boolean enableCrossConversationKnowledge,
        String crossConversationShareMode,
        Integer crossConversationShareLimit,
        java.util.List<Long> selectedSkillIds
) {
}
