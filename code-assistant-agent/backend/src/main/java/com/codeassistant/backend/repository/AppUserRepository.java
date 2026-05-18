package com.codeassistant.backend.repository;

import com.codeassistant.backend.repository.entity.AppUserEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends CrudRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByUsername(String username);

    @Query("SELECT COUNT(*) > 0 FROM app_user WHERE username = :username")
    boolean existsByUsername(@Param("username") String username);
}
