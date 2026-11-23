package com.arslanca.orchestrai.business.dtos.auth;

import com.arslanca.orchestrai.business.dtos.user.UserResponse;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        String refreshToken,
        Long refreshExpiresIn,
        UserResponse user) {

    public AuthResponse {
        tokenType = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
    }

    public static AuthResponse of(final String token, final long expiresInMs, final UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInMs, null, null, user);
    }

    public static AuthResponse withSession(
            final String token,
            final long expiresInMs,
            final String refreshToken,
            final long refreshExpiresInMs,
            final UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInMs, refreshToken, refreshExpiresInMs, user);
    }
}
