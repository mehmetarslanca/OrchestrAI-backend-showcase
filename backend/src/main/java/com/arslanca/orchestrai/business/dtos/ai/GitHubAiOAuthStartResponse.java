package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.time.OffsetDateTime;

public record GitHubAiOAuthStartResponse(
        String provider,
        String surface,
        AiConnectionType connectionType,
        String authorizationUrl,
        String state,
        OffsetDateTime expiresAt) {
}
