package com.arslanca.orchestrai.business.dtos.auth;

import com.arslanca.orchestrai.entity.enums.OAuthProvider;

public record IdentityProfile(
        OAuthProvider provider,
        String providerUserId,
        String username,
        String email,
        String avatarUrl) {
}
