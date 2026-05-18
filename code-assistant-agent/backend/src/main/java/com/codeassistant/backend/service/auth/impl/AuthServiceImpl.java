package com.codeassistant.backend.service.auth.impl;

import com.codeassistant.backend.dto.auth.AuthSessionUser;
import com.codeassistant.backend.dto.auth.AuthTokenResponse;
import com.codeassistant.backend.dto.auth.AuthUserResponse;
import com.codeassistant.backend.dto.auth.ChangePasswordRequest;
import com.codeassistant.backend.dto.auth.LoginRequest;
import com.codeassistant.backend.dto.auth.RegisterRequest;
import com.codeassistant.backend.dto.auth.UpdateProfileRequest;
import com.codeassistant.backend.exception.AuthException;
import com.codeassistant.backend.repository.AppUserRepository;
import com.codeassistant.backend.repository.UserSessionRepository;
import com.codeassistant.backend.repository.entity.AppUserEntity;
import com.codeassistant.backend.repository.entity.UserSessionEntity;
import com.codeassistant.backend.service.auth.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int SESSION_EXPIRE_DAYS = 7;

    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;

    public AuthServiceImpl(AppUserRepository appUserRepository,
                           UserSessionRepository userSessionRepository) {
        this.appUserRepository = appUserRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    public AuthTokenResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (appUserRepository.existsByUsername(username)) {
            throw new AuthException("用户名已存在");
        }

        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setPasswordHash(hashPassword(request.password()));
        user.setNickname(request.nickname().trim());
        user.setCreatedAt(LocalDateTime.now());
        AppUserEntity savedUser = appUserRepository.save(user);
        return createSession(savedUser);
    }

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        AppUserEntity user = appUserRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new AuthException("用户名或密码错误"));

        if (!user.getPasswordHash().equals(hashPassword(request.password()))) {
            throw new AuthException("用户名或密码错误");
        }

        userSessionRepository.deleteByUserId(user.getId());
        return createSession(user);
    }

    @Override
    public AuthUserResponse getCurrentUser(String authorizationHeader) {
        AuthSessionUser user = requireUser(authorizationHeader);
        return new AuthUserResponse(user.id(), user.username(), user.nickname());
    }

    @Override
    public AuthUserResponse updateProfile(String authorizationHeader, UpdateProfileRequest request) {
        AuthSessionUser sessionUser = requireUser(authorizationHeader);
        AppUserEntity user = appUserRepository.findById(sessionUser.id())
                .orElseThrow(() -> new AuthException("用户不存在"));
        user.setNickname(request.nickname().trim());
        AppUserEntity savedUser = appUserRepository.save(user);
        return new AuthUserResponse(savedUser.getId(), savedUser.getUsername(), savedUser.getNickname());
    }

    @Override
    public void changePassword(String authorizationHeader, ChangePasswordRequest request) {
        AuthSessionUser sessionUser = requireUser(authorizationHeader);
        AppUserEntity user = appUserRepository.findById(sessionUser.id())
                .orElseThrow(() -> new AuthException("用户不存在"));
        if (!user.getPasswordHash().equals(hashPassword(request.oldPassword()))) {
            throw new AuthException("原密码错误");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw new AuthException("新密码不能与原密码相同");
        }
        user.setPasswordHash(hashPassword(request.newPassword()));
        appUserRepository.save(user);
        userSessionRepository.deleteByUserId(user.getId());
    }

    @Override
    public void logout(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        if (token != null) {
            userSessionRepository.deleteByToken(token);
        }
    }

    @Override
    public AuthSessionUser resolveUser(String authorizationHeader) {
        userSessionRepository.deleteExpired(LocalDateTime.now());
        String token = extractToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            return null;
        }

        UserSessionEntity session = userSessionRepository.findByToken(token)
                .orElseThrow(() -> new AuthException("登录状态已失效，请重新登录"));

        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            userSessionRepository.deleteByToken(token);
            throw new AuthException("登录状态已失效，请重新登录");
        }

        AppUserEntity user = appUserRepository.findById(session.getUserId())
                .orElseThrow(() -> new AuthException("用户不存在"));

        return new AuthSessionUser(user.getId(), user.getUsername(), user.getNickname());
    }

    private AuthSessionUser requireUser(String authorizationHeader) {
        AuthSessionUser user = resolveUser(authorizationHeader);
        if (user == null) {
            throw new AuthException("请先登录");
        }
        return user;
    }

    private AuthTokenResponse createSession(AppUserEntity user) {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        UserSessionEntity session = new UserSessionEntity();
        session.setUserId(user.getId());
        session.setToken(token);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_EXPIRE_DAYS));
        userSessionRepository.save(session);
        return new AuthTokenResponse(token, new AuthUserResponse(user.getId(), user.getUsername(), user.getNickname()));
    }

    private String extractToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法初始化密码摘要算法", exception);
        }
    }
}
