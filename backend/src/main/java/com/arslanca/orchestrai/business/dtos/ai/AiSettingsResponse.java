package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record AiSettingsResponse(
        String defaultHeavyModelKey,
        CredentialSource defaultAuthSource,
        boolean testModeEnabled,
        List<AvailableProviderDescriptor> availableProviders,
        List<ProviderCredentialStatusResponse> providerCredentials,
        SelectedModelSettingsResponse selectedModel,
        List<AiConnectionDescriptor> aiConnections,
        String activeModelKey,
        AiConnectionExecutionClass activeRoute,
        BigDecimal last30DayEstimatedSpendUsd,
        BigDecimal last30DayReconciledSpendUsd) {

    public AiSettingsResponse {
        availableProviders = availableProviders == null ? List.of() : List.copyOf(availableProviders);
        providerCredentials = providerCredentials == null ? List.of() : List.copyOf(providerCredentials);
        aiConnections = aiConnections == null ? List.of() : List.copyOf(aiConnections);
        last30DayEstimatedSpendUsd = normalize(last30DayEstimatedSpendUsd);
        last30DayReconciledSpendUsd = normalize(last30DayReconciledSpendUsd);
    }

    public AiSettingsResponse(
            final String defaultHeavyModelKey,
            final CredentialSource defaultAuthSource,
            final List<ProviderCredentialStatusResponse> providerCredentials) {
        this(defaultHeavyModelKey, defaultAuthSource, false, List.of(), providerCredentials, null, List.of(), null, null,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public AiSettingsResponse(
            final String defaultHeavyModelKey,
            final CredentialSource defaultAuthSource,
            final boolean testModeEnabled,
            final List<AvailableProviderDescriptor> availableProviders,
            final List<ProviderCredentialStatusResponse> providerCredentials) {
        this(defaultHeavyModelKey, defaultAuthSource, testModeEnabled, availableProviders, providerCredentials, null, List.of(),
                null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public AiSettingsResponse(
            final String defaultHeavyModelKey,
            final CredentialSource defaultAuthSource,
            final boolean testModeEnabled,
            final List<AvailableProviderDescriptor> availableProviders,
            final List<ProviderCredentialStatusResponse> providerCredentials,
            final SelectedModelSettingsResponse selectedModel,
            final List<AiConnectionDescriptor> aiConnections) {
        this(defaultHeavyModelKey, defaultAuthSource, testModeEnabled, availableProviders, providerCredentials, selectedModel,
                aiConnections, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static BigDecimal normalize(final BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
