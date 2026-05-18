package com.codeassistant.backend.repository;

import com.codeassistant.backend.repository.entity.AgentConversationMessageEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgentConversationMessageRepository extends CrudRepository<AgentConversationMessageEntity, Long> {

    @Query("""
            SELECT *
            FROM agent_conversation_message
            WHERE session_id = :sessionId
            ORDER BY turn_index ASC, created_at ASC, id ASC
            """)
    List<AgentConversationMessageEntity> findBySessionId(@Param("sessionId") String sessionId);

    @Query("""
            SELECT COALESCE(MAX(turn_index), 0)
            FROM agent_conversation_message
            WHERE session_id = :sessionId
            """)
    Integer findMaxTurnIndex(@Param("sessionId") String sessionId);

    @Modifying
    @Query("""
            DELETE FROM agent_conversation_message
            WHERE session_id = :sessionId
            """)
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
