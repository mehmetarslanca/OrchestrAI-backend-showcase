package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.time.OffsetDateTime;
import java.util.UUID;

record GitHubAiOAuthState(
        UUID userId,
        AiConnectionType connectionType,
        String surface,
        String stateId,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt) {
}
