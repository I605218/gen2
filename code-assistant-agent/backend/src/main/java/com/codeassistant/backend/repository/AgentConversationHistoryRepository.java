package com.codeassistant.backend.repository;

import com.codeassistant.backend.repository.entity.AgentConversationHistoryEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentConversationHistoryRepository extends CrudRepository<AgentConversationHistoryEntity, Long> {

    @Query("""
            SELECT *
            FROM agent_conversation_history
            ORDER BY pinned DESC, created_at DESC
            LIMIT :limit
            """)
    List<AgentConversationHistoryEntity> findRecent(@Param("limit") int limit);

    @Query("""
            SELECT *
            FROM agent_conversation_history
            WHERE session_id = :sessionId
            ORDER BY pinned DESC, created_at DESC
            LIMIT :limit
            """)
    List<AgentConversationHistoryEntity> findBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Query("""
            SELECT *
            FROM agent_conversation_history
            WHERE user_id = :userId
            ORDER BY pinned DESC, created_at DESC
            LIMIT :limit
            """)
    List<AgentConversationHistoryEntity> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Query("""
            SELECT *
            FROM agent_conversation_history
            WHERE user_id IS NULL AND session_id = :sessionId
            ORDER BY pinned DESC, created_at DESC
            LIMIT :limit
            """)
    List<AgentConversationHistoryEntity> findGuestBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Query("""
            SELECT *
            FROM agent_conversation_history
            WHERE session_id = :sessionId
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Optional<AgentConversationHistoryEntity> findLatestBySessionId(@Param("sessionId") String sessionId);

    @Query("""
            SELECT *
            FROM agent_conversation_history
            WHERE user_id = :userId
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Optional<AgentConversationHistoryEntity> findLatestByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT *
            FROM agent_conversation_history
            WHERE user_id IS NULL AND session_id = :sessionId
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Optional<AgentConversationHistoryEntity> findLatestGuestBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(*) FROM agent_conversation_history")
    long countAll();

    @Query("SELECT COUNT(DISTINCT session_id) FROM agent_conversation_history")
    long countDistinctSessions();

    @Query("SELECT COUNT(DISTINCT user_id) FROM agent_conversation_history WHERE user_id IS NOT NULL")
    long countDistinctUsers();

    @Query("SELECT COUNT(*) FROM agent_conversation_history WHERE task_type = :taskType")
    long countByTaskType(@Param("taskType") String taskType);

    @Query("SELECT COUNT(*) FROM agent_conversation_history WHERE session_id = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(*) FROM agent_conversation_history WHERE user_id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(*) FROM agent_conversation_history WHERE user_id IS NULL AND session_id = :sessionId")
    long countGuestBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(*) > 0 FROM agent_conversation_history WHERE session_id = :sessionId AND pinned = TRUE")
    boolean existsPinnedBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(*) > 0 FROM agent_conversation_history WHERE user_id = :userId AND pinned = TRUE")
    boolean existsPinnedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(*) > 0 FROM agent_conversation_history WHERE user_id IS NULL AND session_id = :sessionId AND pinned = TRUE")
    boolean existsPinnedGuestBySessionId(@Param("sessionId") String sessionId);

    @Query("""
            SELECT task_type
            FROM agent_conversation_history
            GROUP BY task_type
            ORDER BY COUNT(*) DESC
            LIMIT 3
            """)
    List<String> findHotTaskTypes();

    @Modifying
    @Query("UPDATE agent_conversation_history SET user_feedback = :feedback WHERE id = :id")
    void updateFeedback(@Param("id") Long id, @Param("feedback") String feedback);

    @Modifying
    @Query("UPDATE agent_conversation_history SET pinned = :pinned WHERE id = :id")
    void updatePinned(@Param("id") Long id, @Param("pinned") boolean pinned);
}
