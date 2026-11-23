package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProviderCredentialStatusResponse(
        String provider,
        ProviderCredentialStatus status,
        String maskedHint,
        OffsetDateTime lastValidatedAt,
        String displayName,
        boolean testOnly,
        boolean emulated,
        String gateway,
        String runtimeProvider,
        String runtimeTarget,
        String credentialKind,
        UUID connectionId,
        AiConnectionType connectionType,
        AiConnectionStatus connectionStatus,
        AiConnectionAuthMethod authMethod,
        AiConnectionBillingMode billingMode,
        List<String> badges,
        boolean reauthRequired,
        OffsetDateTime lastSuccessfulSyncAt,
        BigDecimal last30DayEstimatedSpendUsd,
        BigDecimal last30DayReconciledSpendUsd,
        String activeModelKey,
        AiConnectionExecutionClass activeRoute) {

    public ProviderCredentialStatusResponse {
        badges = badges == null ? List.of() : List.copyOf(badges);
        last30DayEstimatedSpendUsd = normalize(last30DayEstimatedSpendUsd);
        last30DayReconciledSpendUsd = normalize(last30DayReconciledSpendUsd);
    }

    public ProviderCredentialStatusResponse(
            final String provider,
            final ProviderCredentialStatus status,
            final String maskedHint,
            final OffsetDateTime lastValidatedAt) {
        this(provider, status, maskedHint, lastValidatedAt, provider, false, false, null, null, null, null, null, null, null,
                null, null, List.of(), false, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }

    public ProviderCredentialStatusResponse(
            final String provider,
            final ProviderCredentialStatus status,
            final String maskedHint,
            final OffsetDateTime lastValidatedAt,
            final String displayName,
            final boolean testOnly,
            final boolean emulated,
            final String gateway,
            final String runtimeProvider,
            final String runtimeTarget,
            final String credentialKind,
            final UUID connectionId,
            final AiConnectionType connectionType,
            final AiConnectionStatus connectionStatus,
            final AiConnectionAuthMethod authMethod,
            final AiConnectionBillingMode billingMode,
            final List<String> badges) {
        this(
                provider,
                status,
                maskedHint,
                lastValidatedAt,
                displayName,
                testOnly,
                emulated,
                gateway,
                runtimeProvider,
                runtimeTarget,
                credentialKind,
                connectionId,
                connectionType,
                connectionStatus,
                authMethod,
                billingMode,
                badges,
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null);
    }

    public ProviderCredentialStatusResponse(
            final String provider,
            final ProviderCredentialStatus status,
            final String maskedHint,
            final OffsetDateTime lastValidatedAt,
            final String displayName,
            final boolean testOnly,
            final boolean emulated,
            final String gateway,
            final String runtimeProvider,
            final String runtimeTarget,
            final String credentialKind,
            final List<String> badges) {
        this(
                provider,
                status,
                maskedHint,
                lastValidatedAt,
                displayName,
                testOnly,
                emulated,
                gateway,
                runtimeProvider,
                runtimeTarget,
                credentialKind,
                null,
                null,
                null,
                null,
                null,
                badges,
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null);
    }

    private static BigDecimal normalize(final BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
