package com.arslanca.orchestrai.business.dtos.conversation;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mesaj yanıt DTO'su.
 */
public record MessageResponse(
        UUID id,
        String role,
        String content,
        OffsetDateTime createdAt
) {
}
