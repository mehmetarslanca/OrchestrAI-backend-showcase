package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.HeavyModelCatalogService;
import com.arslanca.orchestrai.business.dtos.ai.CatalogModelAvailabilityState;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelCatalogResponse;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelDescriptor;
import com.arslanca.orchestrai.business.dtos.ai.LightModelDescriptor;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.core.ai.provider.ApprovedHeavyModelRegistry;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.PlatformCredentialRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProductAiProviderRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadata;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadataResolver;
import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HeavyModelCatalogManager implements HeavyModelCatalogService {

    private static final Duration CATALOG_CACHE_TTL = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(HeavyModelCatalogManager.class);

    private final PlatformCredentialRegistry platformCredentialRegistry;
    private final ProviderCredentialManager providerCredentialManager;
    private final List<HeavyProviderAdapter> heavyProviderAdapters;
    private final AiModelProperties aiModelProperties;
    private final AiProviderRuntimeProperties aiProviderRuntimeProperties;
    private final ProviderRuntimeMetadataResolver providerRuntimeMetadataResolver;
    private final Clock clock;
    private volatile CatalogSnapshot catalogSnapshot;

    public HeavyModelCatalogManager(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final ProviderCredentialManager providerCredentialManager,
            final List<HeavyProviderAdapter> heavyProviderAdapters,
            final AiModelProperties aiModelProperties,
            final AiProviderRuntimeProperties aiProviderRuntimeProperties,
            final ProviderRuntimeMetadataResolver providerRuntimeMetadataResolver,
            final Clock clock) {
        this.platformCredentialRegistry = platformCredentialRegistry;
        this.providerCredentialManager = providerCredentialManager;
        this.heavyProviderAdapters = heavyProviderAdapters;
        this.aiModelProperties = aiModelProperties;
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
        this.providerRuntimeMetadataResolver = providerRuntimeMetadataResolver;
        this.clock = clock;
    }

    @Override
    public HeavyModelCatalogResponse getCatalog(final UUID userId) {
        final List<DiscoveredHeavyModel> availableModels = getAvailableModels(userId);
        final List<HeavyModelDescriptor> heavyModels = availableModels.stream()
                .map(this::toDescriptor)
                .filter(HeavyModelDescriptor::available)
                .filter(HeavyModelDescriptor::defaultSelectable)
                .toList();
        final List<HeavyModelDescriptor> catalogModels = buildCatalogModels(userId, availableModels, heavyModels);

        return new HeavyModelCatalogResponse(
                buildLightModelDescriptor(),
                heavyModels,
                catalogModels);
    }

    public List<DiscoveredHeavyModel> getAvailableModels(final UUID userId) {
        final List<DiscoveredHeavyModel> models = new ArrayList<>(getAvailablePlatformModels());
        if (userId != null) {
            models.addAll(providerCredentialManager.getAvailableModels(userId));
        }
        return models.stream()
                .filter(model -> ProductAiProviderRegistry.isVisible(model.provider(), aiProviderRuntimeProperties.testModeEnabled()))
                .sorted(Comparator
                        .comparingInt((DiscoveredHeavyModel model) -> ApprovedHeavyModelRegistry.providerOrder(model.provider()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.familyOrder(model.provider(), model.family()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.sourceOrder(model.source())))
                .toList();
    }

    public List<DiscoveredHeavyModel> getAvailablePlatformModels() {
        final Instant now = clock.instant();
        final CatalogSnapshot currentSnapshot = catalogSnapshot;
        if (currentSnapshot != null && !currentSnapshot.isExpiredAt(now)) {
            return currentSnapshot.models();
        }

        synchronized (this) {
            final CatalogSnapshot latestSnapshot = catalogSnapshot;
            if (latestSnapshot != null && !latestSnapshot.isExpiredAt(now)) {
                return latestSnapshot.models();
            }

            final List<DiscoveredHeavyModel> refreshedModels = loadAvailablePlatformModels();
            catalogSnapshot = new CatalogSnapshot(List.copyOf(refreshedModels), now.plus(CATALOG_CACHE_TTL));
            return refreshedModels;
        }
    }

    private List<DiscoveredHeavyModel> loadAvailablePlatformModels() {
        final List<DiscoveredHeavyModel> discoveredModels = new ArrayList<>();

        for (HeavyProviderAdapter adapter : heavyProviderAdapters) {
            if (!ProductAiProviderRegistry.isVisible(adapter.providerKey(), aiProviderRuntimeProperties.testModeEnabled())) {
                continue;
            }
            if (!adapter.supportsPlatformCredential()) {
                continue;
            }

            platformCredentialRegistry.findPlatformCredential(adapter.providerKey())
                    .ifPresent(credential -> {
                        try {
                            discoveredModels.addAll(adapter.listAvailableHeavyModels(credential));
                        } catch (RestClientResponseException ex) {
                            log.warn("Heavy model catalog lookup skipped for provider={} status={} message={}",
                                    adapter.providerKey(), ex.getStatusCode().value(), ex.getMessage());
                        } catch (Exception ex) {
                            log.warn("Heavy model catalog lookup skipped for provider={}: {}",
                                    adapter.providerKey(), ex.getMessage());
                        }
                    });
        }

        return discoveredModels.stream()
                .filter(model -> ProductAiProviderRegistry.isVisible(model.provider(), aiProviderRuntimeProperties.testModeEnabled()))
                .sorted(Comparator
                        .comparingInt((DiscoveredHeavyModel model) -> ApprovedHeavyModelRegistry.providerOrder(model.provider()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.familyOrder(model.provider(), model.family()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.sourceOrder(model.source())))
                .toList();
    }

    private LightModelDescriptor buildLightModelDescriptor() {
        final String family = aiModelProperties.lightModel();
        final var localFamily = ApprovedHeavyModelRegistry.normalize("openai-compatible", family);
        if (localFamily.isPresent()) {
            return new LightModelDescriptor(
                    "openai-compatible",
                    localFamily.get().family(),
                    localFamily.get().displayName(),
                    true);
        }

        final var googleFamily = ApprovedHeavyModelRegistry.normalize("google", family);
        return new LightModelDescriptor(
                "google",
                googleFamily.map(ApprovedHeavyModelRegistry.ApprovedFamily::family).orElse(family),
                googleFamily.map(ApprovedHeavyModelRegistry.ApprovedFamily::displayName).orElse(family),
                true);
    }

    private HeavyModelDescriptor toDescriptor(final DiscoveredHeavyModel model) {
        final ProviderRuntimeMetadata runtimeMetadata = providerRuntimeMetadataResolver.resolveForModel(
                model.provider(),
                model.family(),
                model.concreteModelId());
        final String connectionSurface = ProductAiProviderRegistry.findConnectionSurface(model.provider(), model.source(), model.connectionType())
                .map(ProductAiProviderRegistry.ProductAiConnectionSurface::surfaceKey)
                .orElse(null);
        return new HeavyModelDescriptor(
                model.modelKey(),
                model.provider(),
                model.family(),
                model.displayName(),
                model.source().name(),
                true,
                true,
                runtimeMetadata.testOnly(),
                runtimeMetadata.emulated(),
                runtimeMetadata.gateway(),
                runtimeMetadata.runtimeProvider(),
                runtimeMetadata.runtimeTarget(),
                runtimeMetadata.badges(),
                true,
                CatalogModelAvailabilityState.SELECTABLE,
                connectionSurface,
                model.selectedConnectionId(),
                model.executionClass());
    }

    private List<HeavyModelDescriptor> buildCatalogModels(
            final UUID userId,
            final List<DiscoveredHeavyModel> availableModels,
            final List<HeavyModelDescriptor> selectableDescriptors) {
        final Map<String, HeavyModelDescriptor> catalogModels = new LinkedHashMap<>();
        selectableDescriptors.forEach(model -> catalogModels.put(modelKeyOf(model), model));

        final Map<AiConnectionType, ProviderCredentialStatusResponse> statusByConnectionType = userId == null
                ? Map.of()
                : providerCredentialManager.getCredentialStatuses(userId).stream()
                        .filter(status -> status.connectionType() != null)
                        .collect(Collectors.toMap(ProviderCredentialStatusResponse::connectionType, Function.identity(), (left, right) -> left));

        ProductAiProviderRegistry.visibleConnectionSurfaces(aiProviderRuntimeProperties.testModeEnabled()).forEach(surface -> {
            final boolean hasSelectableModels = availableModels.stream()
                    .anyMatch(model -> ProductAiProviderRegistry.findConnectionSurface(model.provider(), model.source(), model.connectionType())
                            .map(candidate -> candidate.connectionType() == surface.connectionType())
                            .orElse(false));
            if (hasSelectableModels) {
                return;
            }

            final CatalogModelAvailabilityState availabilityState = placeholderState(surface, statusByConnectionType.get(surface.connectionType()));
            final List<ApprovedHeavyModelRegistry.ApprovedFamily> families = ApprovedHeavyModelRegistry.familiesFor(surface.providerKey());
            if (families.isEmpty()) {
                return;
            }

            families.forEach(family -> {
                final HeavyModelDescriptor placeholder = new HeavyModelDescriptor(
                        surface.providerKey() + ":" + family.family(),
                        surface.providerKey(),
                        family.family(),
                        family.displayName(),
                        surface.credentialSource().name(),
                        false,
                        false,
                        surface.testOnly(),
                        surface.emulated(),
                        null,
                        null,
                        null,
                        surface.badges(),
                        false,
                        availabilityState,
                        surface.surfaceKey(),
                        statusByConnectionType.containsKey(surface.connectionType())
                                ? statusByConnectionType.get(surface.connectionType()).connectionId()
                                : null,
                        defaultExecutionClass(surface.credentialSource()));
                catalogModels.putIfAbsent(modelKeyOf(placeholder), placeholder);
            });
        });

        return catalogModels.values().stream()
                .sorted(Comparator
                        .comparingInt((HeavyModelDescriptor model) -> ApprovedHeavyModelRegistry.providerOrder(model.provider()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.familyOrder(model.provider(), model.family()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.sourceOrder(CredentialSource.valueOf(model.source())))
                        .thenComparing(model -> model.connectionSurface() == null ? "" : model.connectionSurface()))
                .toList();
    }

    private CatalogModelAvailabilityState placeholderState(
            final ProductAiProviderRegistry.ProductAiConnectionSurface surface,
            final ProviderCredentialStatusResponse status) {
        if (surface.deferred()) {
            return CatalogModelAvailabilityState.DEFERRED;
        }
        if (!surface.supported()) {
            return CatalogModelAvailabilityState.UNSUPPORTED;
        }
        if (status == null || status.connectionStatus() == null) {
            return CatalogModelAvailabilityState.LOCKED_CONNECTION_REQUIRED;
        }
        return mapConnectionStatus(status.connectionStatus());
    }

    private CatalogModelAvailabilityState mapConnectionStatus(final AiConnectionStatus connectionStatus) {
        return switch (connectionStatus) {
            case REAUTH_REQUIRED, EXPIRED -> CatalogModelAvailabilityState.REAUTH_REQUIRED;
            case ENTITLEMENT_MISSING -> CatalogModelAvailabilityState.ENTITLEMENT_MISSING;
            case VALID -> CatalogModelAvailabilityState.SELECTABLE;
            case PENDING, INVALID, REVOKED, DEGRADED -> CatalogModelAvailabilityState.LOCKED_CONNECTION_REQUIRED;
        };
    }

    private AiConnectionExecutionClass defaultExecutionClass(final CredentialSource credentialSource) {
        return credentialSource == CredentialSource.PLATFORM
                ? AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE
                : AiConnectionExecutionClass.USER_SINGLE_ROUTE;
    }

    private String modelKeyOf(final HeavyModelDescriptor descriptor) {
        return descriptor.modelKey() + "@" + (descriptor.connectionSurface() == null ? "" : descriptor.connectionSurface());
    }

    private record CatalogSnapshot(List<DiscoveredHeavyModel> models, Instant expiresAt) {

        private boolean isExpiredAt(final Instant instant) {
            return !instant.isBefore(expiresAt);
        }
    }
}
