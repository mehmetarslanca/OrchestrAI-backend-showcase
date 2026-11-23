package com.arslanca.orchestrai.business.dtos.conversation;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Konuşma projesi yanıt DTO'su.
 */
public record ConversationProjectResponse(
        UUID id,
        String name,
        int chatCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
