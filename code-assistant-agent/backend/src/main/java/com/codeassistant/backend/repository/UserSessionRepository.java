package com.codeassistant.backend.repository;

import com.codeassistant.backend.repository.entity.UserSessionEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserSessionRepository extends CrudRepository<UserSessionEntity, Long> {

    @Query("SELECT * FROM user_session WHERE token = :token LIMIT 1")
    Optional<UserSessionEntity> findByToken(@Param("token") String token);

    @Modifying
    @Query("DELETE FROM user_session WHERE token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying
    @Query("DELETE FROM user_session WHERE user_id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM user_session WHERE expires_at < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
