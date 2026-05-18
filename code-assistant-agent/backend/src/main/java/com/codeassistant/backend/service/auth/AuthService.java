package com.codeassistant.backend.service.auth;

import com.codeassistant.backend.dto.auth.AuthSessionUser;
import com.codeassistant.backend.dto.auth.AuthTokenResponse;
import com.codeassistant.backend.dto.auth.AuthUserResponse;
import com.codeassistant.backend.dto.auth.ChangePasswordRequest;
import com.codeassistant.backend.dto.auth.LoginRequest;
import com.codeassistant.backend.dto.auth.RegisterRequest;
import com.codeassistant.backend.dto.auth.UpdateProfileRequest;

public interface AuthService {

    AuthTokenResponse register(RegisterRequest request);

    AuthTokenResponse login(LoginRequest request);

    AuthUserResponse getCurrentUser(String authorizationHeader);

    AuthUserResponse updateProfile(String authorizationHeader, UpdateProfileRequest request);

    void changePassword(String authorizationHeader, ChangePasswordRequest request);

    void logout(String authorizationHeader);

    AuthSessionUser resolveUser(String authorizationHeader);
}
