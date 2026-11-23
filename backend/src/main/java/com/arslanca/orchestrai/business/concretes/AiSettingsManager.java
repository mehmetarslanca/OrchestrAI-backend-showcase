package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiUsageLedgerService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelUserPreferenceService;
import com.arslanca.orchestrai.business.dtos.ai.AiConnectionDescriptor;
import com.arslanca.orchestrai.business.dtos.ai.AiConnectionState;
import com.arslanca.orchestrai.business.dtos.ai.AvailableProviderDescriptor;
import com.arslanca.orchestrai.business.dtos.ai.AiSettingsResponse;
import com.arslanca.orchestrai.business.dtos.ai.AiSettingsUpdateRequest;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelPreference;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.business.dtos.ai.SelectedModelSettingsResponse;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyModelKeyNormalizer;
import com.arslanca.orchestrai.core.ai.provider.ProductAiProviderRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadata;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadataResolver;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiSettingsManager {

    private final HeavyModelCatalogManager heavyModelCatalogManager;
    private final HeavyModelUserPreferenceService heavyModelUserPreferenceService;
    private final ProviderCredentialManager providerCredentialManager;
    private final AiProviderRuntimeProperties aiProviderRuntimeProperties;
    private final ProviderRuntimeMetadataResolver providerRuntimeMetadataResolver;
    private final AiUsageLedgerService aiUsageLedgerService;

    public AiSettingsManager(
            final HeavyModelCatalogManager heavyModelCatalogManager,
            final HeavyModelUserPreferenceService heavyModelUserPreferenceService,
            final ProviderCredentialManager providerCredentialManager,
            final AiProviderRuntimeProperties aiProviderRuntimeProperties,
            final ProviderRuntimeMetadataResolver providerRuntimeMetadataResolver,
            final AiUsageLedgerService aiUsageLedgerService) {
        this.heavyModelCatalogManager = heavyModelCatalogManager;
        this.heavyModelUserPreferenceService = heavyModelUserPreferenceService;
        this.providerCredentialManager = providerCredentialManager;
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
        this.providerRuntimeMetadataResolver = providerRuntimeMetadataResolver;
        this.aiUsageLedgerService = aiUsageLedgerService;
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse getSettings(final UUID userId) {
        final OffsetDateTime windowEnd = OffsetDateTime.now();
        final OffsetDateTime windowStart = windowEnd.minusDays(30);
        final Optional<HeavyModelPreference> preference = heavyModelUserPreferenceService.findPreference(userId);
        final List<DiscoveredHeavyModel> availableModels = heavyModelCatalogManager.getAvailableModels(userId);
        final SelectedModelSettingsResponse selectedModel = preference
                .map(savedPreference -> toSelectedModel(savedPreference, availableModels))
                .orElse(null);
        final List<ProviderCredentialStatusResponse> providerCredentials = enrichProviderCredentials(
                userId,
                providerCredentialManager.getCredentialStatuses(userId),
                selectedModel,
                windowStart,
                windowEnd);
        final var spendSummary = aiUsageLedgerService.summarizeSpend(userId, windowStart, windowEnd);
        return new AiSettingsResponse(
                selectedModel != null ? selectedModel.modelKey() : preference.map(HeavyModelPreference::modelKey).orElse(null),
                selectedModel != null ? selectedModel.authSource() : preference.map(HeavyModelPreference::authSource).orElse(null),
                aiProviderRuntimeProperties.testModeEnabled(),
                availableProviders(),
                providerCredentials,
                selectedModel,
                buildAiConnections(providerCredentials, availableModels, selectedModel),
                selectedModel == null ? null : selectedModel.modelKey(),
                selectedModel == null ? null : selectedModel.executionClass(),
                spendSummary.estimatedCostUsd(),
                spendSummary.reconciledCostUsd());
    }

    @Transactional
    public AiSettingsResponse updateSettings(final UUID userId, final AiSettingsUpdateRequest request) {
        final String requestedModelKey = request.defaultModelKey() != null && !request.defaultModelKey().isBlank()
                ? request.defaultModelKey()
                : request.defaultHeavyModelKey();
        final CredentialSource requestedAuthSource = request.defaultAuthSource();
        final UUID requestedConnectionId = request.defaultConnectionId();

        final boolean clearSelection = requestedModelKey == null || requestedModelKey.isBlank();
        if (clearSelection) {
            if (requestedAuthSource != null || requestedConnectionId != null) {
                throw new IllegalArgumentException("defaultAuthSource/defaultConnectionId, default model olmadan gonderilemez.");
            }
            heavyModelUserPreferenceService.clearPreference(userId);
            return getSettings(userId);
        }

        if (requestedAuthSource == null && requestedConnectionId == null) {
            throw new IllegalArgumentException("defaultAuthSource belirtilmelidir.");
        }

        final String normalizedModelKey = HeavyModelKeyNormalizer.normalize(requestedModelKey);
        final List<DiscoveredHeavyModel> matchingModels = heavyModelCatalogManager.getAvailableModels(userId).stream()
                .filter(model -> model.modelKey().equals(normalizedModelKey))
                .filter(model -> requestedAuthSource == null || model.source() == requestedAuthSource)
                .filter(model -> requestedConnectionId == null || requestedConnectionId.equals(model.selectedConnectionId()))
                .toList();
        if (matchingModels.isEmpty()) {
            throw new IllegalArgumentException("Secilen heavy model bu auth source ile kullanilamiyor.");
        }

        final DiscoveredHeavyModel selectedModel = matchingModels.get(0);
        final CredentialSource effectiveAuthSource = requestedAuthSource != null ? requestedAuthSource : selectedModel.source();
        heavyModelUserPreferenceService.savePreference(
                userId,
                normalizedModelKey,
                effectiveAuthSource,
                requestedConnectionId != null ? requestedConnectionId : selectedModel.selectedConnectionId());
        return getSettings(userId);
    }

    @Transactional(readOnly = true)
    public List<DiscoveredHeavyModel> getAvailableModels(final UUID userId) {
        return heavyModelCatalogManager.getAvailableModels(userId);
    }

    private List<AvailableProviderDescriptor> availableProviders() {
        return ProductAiProviderRegistry.visibleProviders(aiProviderRuntimeProperties.testModeEnabled()).stream()
                .map(provider -> {
                    final ProviderRuntimeMetadata runtimeMetadata = providerRuntimeMetadataResolver.resolveForProvider(provider.providerKey());
                    return new AvailableProviderDescriptor(
                            provider.providerKey(),
                            provider.displayName(),
                            runtimeMetadata.testOnly() || provider.testOnly(),
                            runtimeMetadata.emulated() || provider.emulated(),
                            runtimeMetadata.badges().isEmpty() ? provider.badges() : runtimeMetadata.badges());
                })
                .toList();
    }

    private SelectedModelSettingsResponse toSelectedModel(
            final HeavyModelPreference preference,
            final List<DiscoveredHeavyModel> availableModels) {
        final Optional<DiscoveredHeavyModel> matchedModel = availableModels.stream()
                .filter(model -> model.modelKey().equals(preference.modelKey()))
                .filter(model -> model.source() == preference.authSource())
                .filter(model -> preference.selectedConnectionId() == null
                        || preference.selectedConnectionId().equals(model.selectedConnectionId()))
                .findFirst();
        return new SelectedModelSettingsResponse(
                preference.modelKey(),
                preference.authSource(),
                matchedModel.map(DiscoveredHeavyModel::selectedConnectionId).orElse(preference.selectedConnectionId()),
                matchedModel.map(DiscoveredHeavyModel::executionClass).orElse(defaultExecutionClass(preference.authSource())),
                matchedModel.isPresent());
    }

    private List<AiConnectionDescriptor> buildAiConnections(
            final List<ProviderCredentialStatusResponse> providerCredentials,
            final List<DiscoveredHeavyModel> availableModels,
            final SelectedModelSettingsResponse selectedModel) {
        final Map<AiConnectionType, ProviderCredentialStatusResponse> statusByConnectionType = providerCredentials.stream()
                .filter(status -> status.connectionType() != null)
                .collect(Collectors.toMap(ProviderCredentialStatusResponse::connectionType, Function.identity(), (left, right) -> left));

        return ProductAiProviderRegistry.visibleConnectionSurfaces(aiProviderRuntimeProperties.testModeEnabled()).stream()
                .map(surface -> {
                    final ProviderCredentialStatusResponse status = statusByConnectionType.get(surface.connectionType());
                    final List<DiscoveredHeavyModel> surfaceModels = availableModels.stream()
                            .filter(model -> ProductAiProviderRegistry.findConnectionSurface(model.provider(), model.source(), model.connectionType())
                                    .map(candidate -> candidate.connectionType() == surface.connectionType())
                                    .orElse(false))
                            .toList();
                    final boolean selectable = !surfaceModels.isEmpty();
                    final AiConnectionState state = resolveConnectionState(surface, status, selectable);
                    final UUID connectionId = status != null ? status.connectionId() : null;
                    final boolean selected = matchesSelectedSurface(surface, selectedModel, connectionId);
                    return new AiConnectionDescriptor(
                            surface.surfaceKey(),
                            surface.providerKey(),
                            surface.displayName(),
                            surface.legacyProviderKey(),
                            surface.credentialSource(),
                            surface.authMethod(),
                            surface.billingMode(),
                            state,
                            selectable || state == AiConnectionState.CONNECTED,
                            selectable,
                            surface.supported(),
                            surface.deferred(),
                            connectionId,
                            defaultExecutionClass(surface.credentialSource()),
                            selected,
                            surface.badges(),
                            status == null ? null : status.connectionStatus(),
                            status == null ? null : status.lastValidatedAt(),
                            status != null && status.reauthRequired(),
                            status == null ? null : status.lastSuccessfulSyncAt(),
                            status == null ? null : status.last30DayEstimatedSpendUsd(),
                            status == null ? null : status.last30DayReconciledSpendUsd(),
                            selected && selectedModel != null ? selectedModel.modelKey() : status == null ? null : status.activeModelKey(),
                            selected && selectedModel != null ? selectedModel.executionClass() : status == null ? null : status.activeRoute());
                })
                .toList();
    }

    private List<ProviderCredentialStatusResponse> enrichProviderCredentials(
            final UUID userId,
            final List<ProviderCredentialStatusResponse> providerCredentials,
            final SelectedModelSettingsResponse selectedModel,
            final OffsetDateTime windowStart,
            final OffsetDateTime windowEnd) {
        return providerCredentials.stream()
                .map(status -> {
                    final var spendSummary = status.connectionId() == null
                            ? null
                            : aiUsageLedgerService.summarizeConnectionSpend(userId, status.connectionId(), windowStart, windowEnd);
                    final boolean activeSelection = matchesSelectedStatus(status, selectedModel);
                    return new ProviderCredentialStatusResponse(
                            status.provider(),
                            status.status(),
                            status.maskedHint(),
                            status.lastValidatedAt(),
                            status.displayName(),
                            status.testOnly(),
                            status.emulated(),
                            status.gateway(),
                            status.runtimeProvider(),
                            status.runtimeTarget(),
                            status.credentialKind(),
                            status.connectionId(),
                            status.connectionType(),
                            status.connectionStatus(),
                            status.authMethod(),
                            status.billingMode(),
                            status.badges(),
                            status.reauthRequired(),
                            status.lastSuccessfulSyncAt(),
                            spendSummary == null ? null : spendSummary.estimatedCostUsd(),
                            spendSummary == null ? null : spendSummary.reconciledCostUsd(),
                            activeSelection && selectedModel != null ? selectedModel.modelKey() : null,
                            activeSelection && selectedModel != null ? selectedModel.executionClass() : null);
                })
                .toList();
    }

    private AiConnectionState resolveConnectionState(
            final ProductAiProviderRegistry.ProductAiConnectionSurface surface,
            final ProviderCredentialStatusResponse status,
            final boolean selectable) {
        if (selectable) {
            return AiConnectionState.CONNECTED;
        }
        if (status != null && status.connectionStatus() != null) {
            return mapConnectionStatus(status.connectionStatus());
        }
        if (surface.deferred()) {
            return AiConnectionState.DEFERRED;
        }
        if (!surface.supported()) {
            return AiConnectionState.UNSUPPORTED;
        }
        if (surface.credentialSource() == CredentialSource.PLATFORM) {
            return AiConnectionState.PLATFORM_MANAGED;
        }
        return AiConnectionState.MISSING;
    }

    private AiConnectionState mapConnectionStatus(final AiConnectionStatus connectionStatus) {
        return switch (connectionStatus) {
            case VALID -> AiConnectionState.CONNECTED;
            case REAUTH_REQUIRED, EXPIRED -> AiConnectionState.REAUTH_REQUIRED;
            case ENTITLEMENT_MISSING -> AiConnectionState.ENTITLEMENT_MISSING;
            case PENDING -> AiConnectionState.PENDING;
            case INVALID, REVOKED, DEGRADED -> AiConnectionState.INVALID;
        };
    }

    private boolean matchesSelectedSurface(
            final ProductAiProviderRegistry.ProductAiConnectionSurface surface,
            final SelectedModelSettingsResponse selectedModel,
            final UUID connectionId) {
        if (selectedModel == null || selectedModel.authSource() != surface.credentialSource()) {
            return false;
        }

        final String selectedProviderKey = providerKeyOf(selectedModel.modelKey());
        if (!surface.providerKey().equals(selectedProviderKey)) {
            return false;
        }

        return selectedModel.selectedConnectionId() == null || selectedModel.selectedConnectionId().equals(connectionId);
    }

    private boolean matchesSelectedStatus(
            final ProviderCredentialStatusResponse status,
            final SelectedModelSettingsResponse selectedModel) {
        if (status == null || selectedModel == null) {
            return false;
        }
        if (selectedModel.selectedConnectionId() != null) {
            return selectedModel.selectedConnectionId().equals(status.connectionId());
        }
        return providerKeyOf(selectedModel.modelKey()).equals(status.provider());
    }

    private AiConnectionExecutionClass defaultExecutionClass(final CredentialSource credentialSource) {
        return credentialSource == CredentialSource.PLATFORM
                ? AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE
                : AiConnectionExecutionClass.USER_SINGLE_ROUTE;
    }

    private String providerKeyOf(final String modelKey) {
        final int separatorIndex = modelKey == null ? -1 : modelKey.indexOf(':');
        return separatorIndex < 0 ? modelKey : modelKey.substring(0, separatorIndex);
    }
}
