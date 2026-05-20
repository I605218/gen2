package com.codeassistant.backend.dto.agent;

import com.codeassistant.backend.model.agent.AgentTaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AgentRequest(
        @NotNull(message = "taskType 不能为空")
        AgentTaskType taskType,
        @NotBlank(message = "question 不能为空")
        String question,
        String code,
        String language,
        String errorMessage,
        String knowledgePoint,
        Integer practiceCount,
        Boolean enableReflexion,
        List<String> selectedTools,
        Long userId
) {
}
