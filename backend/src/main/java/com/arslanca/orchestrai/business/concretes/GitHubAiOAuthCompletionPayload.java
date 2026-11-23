package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.util.List;
import java.util.UUID;

record GitHubAiOAuthCompletionPayload(
        UUID userId,
        AiConnectionType connectionType,
        String surface,
        String stateId,
        String providerAccountSubject,
        String providerAccountLabel,
        String accessToken,
        String refreshToken,
        String tokenType,
        String scope,
        List<String> scopes,
        String tokenExpiresAt,
        String authorizedAt,
        String completionExpiresAt) {

    GitHubAiOAuthCompletionPayload {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
