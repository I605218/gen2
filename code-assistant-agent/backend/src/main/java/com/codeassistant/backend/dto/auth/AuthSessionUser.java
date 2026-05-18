package com.codeassistant.backend.dto.auth;

public record AuthSessionUser(
        Long id,
        String username,
        String nickname
) {
}
