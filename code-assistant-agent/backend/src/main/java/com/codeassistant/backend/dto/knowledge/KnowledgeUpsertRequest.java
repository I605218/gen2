package com.codeassistant.backend.dto.knowledge;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record KnowledgeUpsertRequest(
        @NotBlank(message = "title 不能为空")
        String title,
        String sourceName,
        String sourceType,
        String tags,
        String content,
        Boolean enabled,
        String summary,
        List<String> aliases,
        List<String> categories,
        List<String> references
) {
}
