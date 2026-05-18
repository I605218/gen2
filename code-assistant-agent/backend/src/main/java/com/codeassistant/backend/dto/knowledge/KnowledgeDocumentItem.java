package com.codeassistant.backend.dto.knowledge;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeDocumentItem(
        Long id,
        String title,
        String sourceName,
        String sourceType,
        String tags,
        String summary,
        List<String> aliases,
        List<String> categories,
        List<String> references,
        Boolean enabled,
        Integer chunkCount,
        Integer totalChars,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
