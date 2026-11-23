package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AiConnectionDescriptor(
        String connectionSurface,
        String provider,
        String displayName,
        String legacyProviderKey,
        CredentialSource authSource,
        AiConnectionAuthMethod authMethod,
        AiConnectionBillingMode billingMode,
        AiConnectionState state,
        boolean connected,
        boolean selectable,
        boolean supported,
        boolean deferred,
        UUID connectionId,
        AiConnectionExecutionClass executionClass,
        boolean selected,
        List<String> badges,
        AiConnectionStatus connectionStatus,
        OffsetDateTime lastValidatedAt,
        boolean reauthRequired,
        OffsetDateTime lastSuccessfulSyncAt,
        BigDecimal last30DayEstimatedSpendUsd,
        BigDecimal last30DayReconciledSpendUsd,
        String activeModelKey,
        AiConnectionExecutionClass activeRoute) {

    public AiConnectionDescriptor {
        badges = badges == null ? List.of() : List.copyOf(badges);
        last30DayEstimatedSpendUsd = normalize(last30DayEstimatedSpendUsd);
        last30DayReconciledSpendUsd = normalize(last30DayReconciledSpendUsd);
    }

    public AiConnectionDescriptor(
            final String connectionSurface,
            final String provider,
            final String displayName,
            final String legacyProviderKey,
            final CredentialSource authSource,
            final AiConnectionAuthMethod authMethod,
            final AiConnectionBillingMode billingMode,
            final AiConnectionState state,
            final boolean connected,
            final boolean selectable,
            final boolean supported,
            final boolean deferred,
            final UUID connectionId,
            final AiConnectionExecutionClass executionClass,
            final boolean selected,
            final List<String> badges) {
        this(connectionSurface, provider, displayName, legacyProviderKey, authSource, authMethod, billingMode, state,
                connected, selectable, supported, deferred, connectionId, executionClass, selected, badges, null, null,
                false, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }

    private static BigDecimal normalize(final BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
