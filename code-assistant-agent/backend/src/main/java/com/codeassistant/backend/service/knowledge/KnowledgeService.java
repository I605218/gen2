package com.codeassistant.backend.service.knowledge;

import com.codeassistant.backend.dto.knowledge.KnowledgeDocumentItem;
import com.codeassistant.backend.dto.knowledge.KnowledgeSearchResponse;
import com.codeassistant.backend.dto.knowledge.KnowledgeUpsertRequest;

import java.util.List;

public interface KnowledgeService {
    List<KnowledgeDocumentItem> listDocuments(Long userId);
    KnowledgeDocumentItem getDocument(Long userId, Long id);
    KnowledgeDocumentItem upsertDocument(Long userId, Long id, KnowledgeUpsertRequest request);
    void deleteDocument(Long userId, Long id);
    KnowledgeSearchResponse search(Long userId, String query, int limit);
    List<KnowledgeDocumentItem> importSamples(Long userId);
}
