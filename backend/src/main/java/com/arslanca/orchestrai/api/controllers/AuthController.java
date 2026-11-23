package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.dtos.auth.AuthResponse;
import com.arslanca.orchestrai.business.dtos.auth.GoogleTokenRequest;
import com.arslanca.orchestrai.business.dtos.auth.GitHubTokenRequest;
import com.arslanca.orchestrai.business.dtos.auth.RefreshTokenRequest;
import com.arslanca.orchestrai.business.abstracts.AuthService;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }


    /**
     * POST /api/v1/auth/github/callback
     *
     * GitHub OAuth authorization code'u alır,
     * JWT token üretir ve döndürür.
     *
     * Request Body: { "code": "github_auth_code" }
     * Response: { "token": "...", "tokenType": "Bearer", "expiresIn": 86400000,
     * "user": {...} }
     */
    @PostMapping("/github/callback")
    public ResponseEntity<AuthResponse> authenticateWithGitHub(
            @Valid @RequestBody final GitHubTokenRequest request) {
        final AuthResponse response = authService.authenticateWithGitHub(request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google/callback")
    public ResponseEntity<AuthResponse> authenticateWithGoogle(
            @Valid @RequestBody final GoogleTokenRequest request) {
        final AuthResponse response = authService.authenticateWithGoogle(request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshSession(
            @Valid @RequestBody final RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshSession(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser) {
        authService.logout(authenticatedUser == null ? null : authenticatedUser.sessionId());
        return ResponseEntity.noContent().build();
    }
}
