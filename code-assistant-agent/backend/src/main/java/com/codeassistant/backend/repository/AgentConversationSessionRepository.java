package com.codeassistant.backend.repository;

import com.codeassistant.backend.repository.entity.AgentConversationSessionEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentConversationSessionRepository extends CrudRepository<AgentConversationSessionEntity, Long> {

    @Query("""
            SELECT *
            FROM agent_conversation_session
            WHERE user_id = :userId
            ORDER BY updated_at DESC, created_at DESC
            """)
    List<AgentConversationSessionEntity> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT *
            FROM agent_conversation_session
            WHERE user_id IS NULL AND session_id = :sessionId
            LIMIT 1
            """)
    Optional<AgentConversationSessionEntity> findGuestBySessionId(@Param("sessionId") String sessionId);

    @Query("""
            SELECT *
            FROM agent_conversation_session
            WHERE user_id = :userId AND session_id = :sessionId
            LIMIT 1
            """)
    Optional<AgentConversationSessionEntity> findByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Modifying
    @Query("""
            UPDATE agent_conversation_session
            SET title = :title, updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """)
    void updateTitle(@Param("id") Long id, @Param("title") String title);

    @Modifying
    @Query("""
            UPDATE agent_conversation_session
            SET summary = :summary, updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """)
    void updateSummary(@Param("id") Long id, @Param("summary") String summary);

    @Modifying
    @Query("""
            DELETE FROM agent_conversation_session
            WHERE session_id = :sessionId
            """)
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
