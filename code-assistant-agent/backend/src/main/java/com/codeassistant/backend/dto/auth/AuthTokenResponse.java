package com.codeassistant.backend.dto.auth;

public record AuthTokenResponse(
        String token,
        AuthUserResponse user
) {
}
