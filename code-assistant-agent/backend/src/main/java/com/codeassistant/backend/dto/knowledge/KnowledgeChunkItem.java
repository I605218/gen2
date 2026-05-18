package com.codeassistant.backend.dto.knowledge;

public record KnowledgeChunkItem(
        Long id,
        Long documentId,
        Integer chunkIndex,
        Integer startOffset,
        Integer endOffset,
        String title,
        String content,
        String keywords,
        Double keywordScore,
        Double lengthScore,
        Double finalScore
) {
}
