package com.codeassistant.backend.repository;

import com.codeassistant.backend.repository.entity.KnowledgeChunkEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeChunkRepository extends CrudRepository<KnowledgeChunkEntity, Long> {

    @Query("""
            SELECT *
            FROM agent_knowledge_chunk
            WHERE user_id = :userId
            ORDER BY updated_at DESC, chunk_index ASC
            LIMIT :limit
            """)
    List<KnowledgeChunkEntity> searchTopChunks(@Param("userId") Long userId, @Param("limit") int limit);

    @Query("""
            SELECT *
            FROM agent_knowledge_chunk
            WHERE user_id = :userId
            ORDER BY updated_at DESC, chunk_index ASC
            """)
    List<KnowledgeChunkEntity> findSearchCandidates(@Param("userId") Long userId);

    @Query("""
            SELECT *
            FROM agent_knowledge_chunk
            WHERE user_id = :userId AND document_id = :documentId
            ORDER BY chunk_index ASC
            """)
    List<KnowledgeChunkEntity> findByUserIdAndDocumentId(@Param("userId") Long userId, @Param("documentId") Long documentId);

    @Modifying
    @Query("""
            DELETE FROM agent_knowledge_chunk
            WHERE user_id = :userId AND document_id = :documentId
            """)
    void deleteByUserIdAndDocumentId(@Param("userId") Long userId, @Param("documentId") Long documentId);
}
