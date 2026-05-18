package com.codeassistant.backend.controller;

import com.codeassistant.backend.dto.auth.AuthTokenResponse;
import com.codeassistant.backend.dto.auth.AuthUserResponse;
import com.codeassistant.backend.dto.auth.ChangePasswordRequest;
import com.codeassistant.backend.dto.auth.LoginRequest;
import com.codeassistant.backend.dto.auth.RegisterRequest;
import com.codeassistant.backend.dto.auth.UpdateProfileRequest;
import com.codeassistant.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthTokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return authService.getCurrentUser(authorizationHeader);
    }

    @PostMapping("/profile")
    public AuthUserResponse updateProfile(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                          @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(authorizationHeader, request);
    }

    @PostMapping("/change-password")
    public void changePassword(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authorizationHeader, request);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
    }
}
