package com.codeassistant.backend.dto.knowledge;

public record KnowledgeSearchResultItem(
        Long documentId,
        Long chunkId,
        Integer chunkIndex,
        String title,
        String sourceName,
        String sourceType,
        String tags,
        String summary,
        String references,
        String content,
        String keywords,
        Double keywordScore,
        Double lengthScore,
        Double score,
        Integer startOffset,
        Integer endOffset
) {
}
