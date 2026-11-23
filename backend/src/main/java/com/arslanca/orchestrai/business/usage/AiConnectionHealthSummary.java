package com.arslanca.orchestrai.business.usage;

import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiConnectionHealthSummary(
        UUID connectionId,
        String providerKey,
        AiConnectionStatus connectionStatus,
        OffsetDateTime lastValidatedAt,
        boolean reauthRequired,
        OffsetDateTime lastSuccessfulUsageAt,
        OffsetDateTime lastSuccessfulSyncAt) {
}
