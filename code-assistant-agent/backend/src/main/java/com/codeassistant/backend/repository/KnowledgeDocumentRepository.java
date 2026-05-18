package com.codeassistant.backend.repository;

import com.codeassistant.backend.repository.entity.KnowledgeDocumentEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends CrudRepository<KnowledgeDocumentEntity, Long> {

    @Query("""
            SELECT *
            FROM agent_knowledge_document
            WHERE user_id = :userId
            ORDER BY updated_at DESC, created_at DESC
            """)
    List<KnowledgeDocumentEntity> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT *
            FROM agent_knowledge_document
            WHERE user_id = :userId AND id = :id
            LIMIT 1
            """)
    Optional<KnowledgeDocumentEntity> findByUserIdAndId(@Param("userId") Long userId, @Param("id") Long id);

    @Query("""
            SELECT COUNT(*)
            FROM agent_knowledge_chunk
            WHERE user_id = :userId AND document_id = :documentId
            """)
    Integer countChunks(@Param("userId") Long userId, @Param("documentId") Long documentId);
}
