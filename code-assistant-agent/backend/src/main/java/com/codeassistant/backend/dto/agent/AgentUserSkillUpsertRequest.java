package com.codeassistant.backend.dto.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentUserSkillUpsertRequest(
        @NotBlank(message = "name 不能为空")
        String name,
        String description,
        @NotBlank(message = "content 不能为空")
        String content,
        Boolean enabled
) {
}
