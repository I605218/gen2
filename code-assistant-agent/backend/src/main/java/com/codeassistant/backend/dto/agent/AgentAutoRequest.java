package com.codeassistant.backend.dto.agent;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AgentAutoRequest(
        @NotBlank(message = "message 不能为空")
        String message,
        String code,
        Boolean enableReflexion,
        List<String> selectedTools
) {
}
