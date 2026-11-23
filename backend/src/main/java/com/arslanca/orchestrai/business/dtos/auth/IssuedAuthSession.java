package com.arslanca.orchestrai.business.dtos.auth;

import com.arslanca.orchestrai.entity.concretes.UserEntity;

import java.util.UUID;

public record IssuedAuthSession(
        UserEntity user,
        UUID sessionId,
        String accessToken,
        String refreshToken,
        long accessExpiresInMs,
        long refreshExpiresInMs) {
}
