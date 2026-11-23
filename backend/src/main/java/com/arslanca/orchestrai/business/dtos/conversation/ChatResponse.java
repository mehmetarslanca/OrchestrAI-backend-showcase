package com.arslanca.orchestrai.business.dtos.conversation;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chat session yanıt DTO'su.
 */
public record ChatResponse(
        UUID id,
        String title,
        int messageCount,
        OffsetDateTime createdAt
) {
}
