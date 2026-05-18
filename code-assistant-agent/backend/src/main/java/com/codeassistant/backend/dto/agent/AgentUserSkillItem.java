package com.codeassistant.backend.dto.agent;

import java.time.LocalDateTime;

public record AgentUserSkillItem(
        Long id,
        String name,
        String description,
        String content,
        Boolean enabled,
        LocalDateTime updatedAt
) {
}
