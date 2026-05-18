package com.codeassistant.backend.dto.auth;

public record AuthUserResponse(
        Long id,
        String username,
        String nickname
) {
}
