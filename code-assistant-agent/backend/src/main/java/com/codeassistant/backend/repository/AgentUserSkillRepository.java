package com.codeassistant.backend.repository;

import com.codeassistant.backend.repository.entity.AgentUserSkillEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentUserSkillRepository extends CrudRepository<AgentUserSkillEntity, Long> {

    @Query("""
            SELECT *
            FROM agent_user_skill
            WHERE user_id = :userId
            ORDER BY updated_at DESC, created_at DESC
            """)
    List<AgentUserSkillEntity> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT *
            FROM agent_user_skill
            WHERE user_id = :userId AND id = :id
            LIMIT 1
            """)
    Optional<AgentUserSkillEntity> findByUserIdAndId(@Param("userId") Long userId, @Param("id") Long id);

    @Modifying
    @Query("""
            DELETE FROM agent_user_skill
            WHERE user_id = :userId AND id = :id
            """)
    int deleteByUserIdAndId(@Param("userId") Long userId, @Param("id") Long id);
}
