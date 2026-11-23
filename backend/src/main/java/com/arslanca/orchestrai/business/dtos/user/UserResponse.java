package com.arslanca.orchestrai.business.dtos.user;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String avatarUrl,
        String provider,
        String role,
        OffsetDateTime createdAt) {
}
