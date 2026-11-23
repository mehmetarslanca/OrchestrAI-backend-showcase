package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.time.OffsetDateTime;

public record GitHubAiOAuthCallbackResponse(
        String provider,
        String surface,
        AiConnectionType connectionType,
        String completionToken,
        String providerAccountSubject,
        String providerAccountLabel,
        OffsetDateTime expiresAt) {
}
