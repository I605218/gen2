package com.codeassistant.backend.dto.knowledge;

import java.util.List;

public record KnowledgeSearchResponse(
        String query,
        List<KnowledgeSearchResultItem> results
) {
}
