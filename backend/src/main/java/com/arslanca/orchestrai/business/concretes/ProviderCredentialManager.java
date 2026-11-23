package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.core.ai.provider.ApprovedHeavyModelRegistry;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProductAiProviderRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;



import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadata;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadataResolver;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.arslanca.orchestrai.core.security.EncryptedSecret;
import com.arslanca.orchestrai.core.security.SecretEncryptionService;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ProviderCredentialManager {

    private static final int MAX_API_KEY_LENGTH = 1000;
    private static final TypeReference<List<DiscoveredHeavyModel>> DISCOVERED_MODEL_LIST_TYPE =
            new TypeReference<>() {
            };
    private static final Map<String, AiConnectionType> BYOK_CONNECTION_TYPES_BY_PROVIDER_KEY = Map.ofEntries(
            Map.entry("openai", AiConnectionType.OPENAI_BYOK),
            Map.entry("google", AiConnectionType.GOOGLE_BYOK),
            Map.entry("anthropic", AiConnectionType.ANTHROPIC_BYOK),
            Map.entry("ollama", AiConnectionType.OLLAMA_BYOK),
            Map.entry("openai-compatible", AiConnectionType.OPENAI_COMPATIBLE_BYOK),
            Map.entry("moonshot", AiConnectionType.MOONSHOT_BYOK),
            Map.entry("zai", AiConnectionType.ZAI_BYOK),
            Map.entry("xai", AiConnectionType.XAI_BYOK));
    private static final EnumSet<AiConnectionStatus> ACTIVE_CONNECTION_STATUSES = EnumSet.of(
            AiConnectionStatus.PENDING,
            AiConnectionStatus.VALID,
            AiConnectionStatus.INVALID,
            AiConnectionStatus.EXPIRED,
            AiConnectionStatus.REAUTH_REQUIRED,
            AiConnectionStatus.ENTITLEMENT_MISSING,
            AiConnectionStatus.DEGRADED);
    private static final Set<String> VALIDATION_METADATA_KEYS = Set.of(
            "discoveredModels",
            "catalogRefreshedAt",
            "testMode",
            "testOnly",
            "emulated",
            "gateway",
            "runtimeProvider",
            "runtimeTarget",
            "credentialKind",
            "badges");

    private final UserAiConnectionRepository userAiConnectionRepository;
    private final UserLinkedConnectionRuntimeManager userLinkedConnectionRuntimeManager;
    private final SecretEncryptionService secretEncryptionService;
    private final ObjectMapper objectMapper;
    private final List<HeavyProviderAdapter> heavyProviderAdapters;
    private final AiProviderRuntimeProperties aiProviderRuntimeProperties;
    private final ProviderRuntimeMetadataResolver providerRuntimeMetadataResolver;

    public ProviderCredentialManager(
            final UserAiConnectionRepository userAiConnectionRepository,
            final UserLinkedConnectionRuntimeManager userLinkedConnectionRuntimeManager,
            final SecretEncryptionService secretEncryptionService,
            final ObjectMapper objectMapper,
            final List<HeavyProviderAdapter> heavyProviderAdapters,
            final AiProviderRuntimeProperties aiProviderRuntimeProperties,
            final ProviderRuntimeMetadataResolver providerRuntimeMetadataResolver) {
        this.userAiConnectionRepository = userAiConnectionRepository;
        this.userLinkedConnectionRuntimeManager = userLinkedConnectionRuntimeManager;
        this.secretEncryptionService = secretEncryptionService;
        this.objectMapper = objectMapper;
        this.heavyProviderAdapters = List.copyOf(heavyProviderAdapters);
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
        this.providerRuntimeMetadataResolver = providerRuntimeMetadataResolver;
    }

    public ProviderCredentialStatusResponse saveCredential(final UUID userId, final String rawProviderKey, final String rawApiKey) {
        final String providerKey = requireVisibleProductProvider(normalizeProviderKey(rawProviderKey));
        final AiConnectionType connectionType = requireByokConnectionType(providerKey);
        final String apiKey = normalizeApiKey(rawApiKey);
        final HeavyProviderAdapter adapter = requireAdapter(providerKey);
        final ProviderRuntimeMetadata runtimeMetadata = providerRuntimeMetadataResolver.resolveForCredential(providerKey, apiKey);

        final EncryptedSecret encryptedSecret = secretEncryptionService.encrypt(apiKey);
        final UserAiConnectionEntity entity = userAiConnectionRepository.findByUserIdAndConnectionType(userId, connectionType)
                .orElseGet(() -> UserAiConnectionEntity.createPendingByok(
                        userId,
                        connectionType,
                        providerKey,
                        secretEncryptionService.currentKeyVersion()));

        entity.markPending(
                encryptedSecret.payload(),
                maskSecret(apiKey),
                encryptedSecret.keyVersion(),
                withoutValidationMetadata(entity.getMetadata()));
        userAiConnectionRepository.save(entity);

        final ProviderCredential providerCredential = new ProviderCredential(
                providerKey,
                apiKey,
                CredentialSource.BYOK,
                userId.toString(),
                entity.getId());

        try {
            final List<DiscoveredHeavyModel> discoveredModels = adapter.listAvailableHeavyModels(providerCredential);
            entity.markValid(mergeValidationMetadata(entity.getMetadata(), metadataForModels(discoveredModels, runtimeMetadata)));
            userAiConnectionRepository.save(entity);
            return toStatusResponse(entity);
        } catch (final RestClientResponseException ex) {
            if (isInvalidCredentialStatus(ex.getStatusCode().value())) {
                entity.markInvalid(mergeValidationMetadata(entity.getMetadata(), metadataForModels(List.of(), runtimeMetadata)));
                userAiConnectionRepository.save(entity);
                return toStatusResponse(entity);
            }
            throw new InfrastructureUnavailableException(
                    "Provider credential validation temporarily unavailable for provider=" + providerKey,
                    ex);
        } catch (final Exception ex) {
            throw new InfrastructureUnavailableException(
                    "Provider credential validation temporarily unavailable for provider=" + providerKey,
                    ex);
        }
    }

    @Transactional
    public void revokeCredential(final UUID userId, final String rawProviderKey) {
        final String providerKey = requireKnownProductProvider(normalizeProviderKey(rawProviderKey));
        final AiConnectionType connectionType = requireByokConnectionType(providerKey);
        userAiConnectionRepository.findByUserIdAndConnectionType(userId, connectionType)
                .ifPresent(entity -> {
                    entity.markRevoked(withoutValidationMetadata(entity.getMetadata()));
                    userAiConnectionRepository.save(entity);
                });
    }

    @Transactional(readOnly = true)
    public List<ProviderCredentialStatusResponse> getCredentialStatuses(final UUID userId) {
        return userAiConnectionRepository.findAllByUserIdAndConnectionStatusIn(userId, ACTIVE_CONNECTION_STATUSES)
                .stream()
                .filter(this::isVisibleConnection)
                .sorted(Comparator.comparingInt(this::displayOrderOf))
                .map(this::toStatusResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProviderCredentialStatusResponse> findConnectionStatus(final UUID userId, final AiConnectionType connectionType) {
        return userAiConnectionRepository.findByUserIdAndConnectionType(userId, connectionType)
                .filter(this::isVisibleConnection)
                .map(this::toStatusResponse);
    }

    @Transactional
    public List<DiscoveredHeavyModel> getAvailableModels(final UUID userId) {
        final List<DiscoveredHeavyModel> availableModels = new ArrayList<>();
        for (UserAiConnectionEntity entity : userAiConnectionRepository.findAllByUserIdAndConnectionStatusIn(
                userId,
                EnumSet.of(AiConnectionStatus.VALID))) {
            UserAiConnectionEntity effectiveEntity = entity;
            if (isVisibleUserLinkedConnection(entity) && isRuntimeSyncRequired(entity)) {
                effectiveEntity = userLinkedConnectionRuntimeManager.synchronizeConnection(userId, entity.getId())
                        .orElse(entity);
            }
            if (!isVisibleConnection(effectiveEntity) || effectiveEntity.getConnectionStatus() != AiConnectionStatus.VALID) {
                continue;
            }
            availableModels.addAll(readDiscoveredModels(effectiveEntity).stream()
                    .filter(model -> ProductAiProviderRegistry.isVisible(model.provider(), aiProviderRuntimeProperties.testModeEnabled()))
                    .toList());
        }
        return availableModels.stream()
                .sorted(Comparator
                        .comparingInt((DiscoveredHeavyModel model) -> ApprovedHeavyModelRegistry.providerOrder(model.provider()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.familyOrder(model.provider(), model.family()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.sourceOrder(model.source())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProviderCredential> findValidCredential(final UUID userId, final String rawProviderKey) {
        final String providerKey = normalizeProviderKey(rawProviderKey);
        if (!ProductAiProviderRegistry.isVisible(providerKey, aiProviderRuntimeProperties.testModeEnabled())) {
            return Optional.empty();
        }
        return byokConnectionTypeFor(providerKey)
                .flatMap(connectionType -> userAiConnectionRepository.findByUserIdAndConnectionType(userId, connectionType))
                .filter(this::isValidByokConnection)
                .filter(entity -> entity.getEncryptedApiKey() != null && !entity.getEncryptedApiKey().isBlank())
                .map(entity -> toProviderCredential(entity, userId));
    }

    public Optional<ProviderCredential> findValidCredential(final UUID userId, final UUID connectionId) {
        if (connectionId == null) {
            return Optional.empty();
        }
        final Optional<UserAiConnectionEntity> connection = userAiConnectionRepository.findByIdAndUserId(connectionId, userId);
        if (connection.isEmpty()) {
            return Optional.empty();
        }
        final UserAiConnectionEntity entity = connection.get();
        if (isVisibleUserLinkedConnection(entity)) {
            return userLinkedConnectionRuntimeManager.resolveValidCredential(userId, connectionId);
        }
        return Optional.of(entity)
                .filter(this::isValidByokConnection)
                .filter(candidate -> candidate.getEncryptedApiKey() != null && !candidate.getEncryptedApiKey().isBlank())
                .map(candidate -> toProviderCredential(candidate, userId));
    }

    private HeavyProviderAdapter requireAdapter(final String providerKey) {
        return heavyProviderAdapters.stream()
                .filter(adapter -> providerKey.equals(adapter.providerKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Desteklenmeyen provider: " + providerKey));
    }

    private String requireKnownProductProvider(final String providerKey) {
        if (!ProductAiProviderRegistry.isKnown(providerKey)) {
            throw new IllegalArgumentException("Desteklenmeyen provider: " + providerKey);
        }
        return providerKey;
    }

    private String requireVisibleProductProvider(final String providerKey) {
        requireKnownProductProvider(providerKey);
        if (!ProductAiProviderRegistry.isVisible(providerKey, aiProviderRuntimeProperties.testModeEnabled())) {
            throw new IllegalArgumentException("Provider bu ortamda aktif degil: " + providerKey);
        }
        return providerKey;
    }

    private Optional<AiConnectionType> byokConnectionTypeFor(final String providerKey) {
        return Optional.ofNullable(BYOK_CONNECTION_TYPES_BY_PROVIDER_KEY.get(providerKey));
    }

    private AiConnectionType requireByokConnectionType(final String providerKey) {
        return byokConnectionTypeFor(providerKey)
                .orElseThrow(() -> new IllegalArgumentException("Desteklenmeyen provider: " + providerKey));
    }

    private String normalizeProviderKey(final String rawProviderKey) {
        if (rawProviderKey == null || rawProviderKey.isBlank()) {
            throw new IllegalArgumentException("Provider bos olamaz.");
        }
        return rawProviderKey.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeApiKey(final String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new IllegalArgumentException("API key bos olamaz.");
        }

        final String normalizedApiKey = rawApiKey.trim();
        if (normalizedApiKey.length() > MAX_API_KEY_LENGTH) {
            throw new IllegalArgumentException("API key en fazla " + MAX_API_KEY_LENGTH + " karakter olabilir.");
        }
        return normalizedApiKey;
    }

    private boolean isInvalidCredentialStatus(final int statusCode) {
        return statusCode == 400 || statusCode == 401 || statusCode == 403;
    }

    private String maskSecret(final String secret) {
        if (secret.length() <= 8) {
            return "***" + secret.substring(Math.max(0, secret.length() - 2));
        }
        return secret.substring(0, 4) + "..." + secret.substring(secret.length() - 4);
    }

    private Map<String, Object> metadataForModels(
            final List<DiscoveredHeavyModel> discoveredModels,
            final ProviderRuntimeMetadata runtimeMetadata) {
        final List<Map<String, Object>> serializedModels = discoveredModels.stream()
                .map(model -> {
                    final Map<String, Object> modelMap = new LinkedHashMap<>();
                    modelMap.put("provider", model.provider());
                    modelMap.put("family", model.family());
                    modelMap.put("concreteModelId", model.concreteModelId());
                    modelMap.put("displayName", model.displayName());
                    modelMap.put("source", model.source().name());
                    modelMap.put("credentialOwner", model.credentialOwner());
                    putIfPresent(modelMap, "selectedConnectionId",
                            model.selectedConnectionId() == null ? null : model.selectedConnectionId().toString());
                    modelMap.put("executionClass", model.executionClass().name());
                    putIfPresent(modelMap, "connectionType", model.connectionType() == null ? null : model.connectionType().name());
                    return modelMap;
                })
                .toList();

        final Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("discoveredModels", serializedModels);
        metadata.put("catalogRefreshedAt", OffsetDateTime.now().toString());
        metadata.put("testMode", aiProviderRuntimeProperties.testModeEnabled());
        metadata.put("testOnly", runtimeMetadata.testOnly());
        metadata.put("emulated", runtimeMetadata.emulated());
        putIfPresent(metadata, "gateway", runtimeMetadata.gateway());
        putIfPresent(metadata, "runtimeProvider", runtimeMetadata.runtimeProvider());
        putIfPresent(metadata, "runtimeTarget", runtimeMetadata.runtimeTarget());
        putIfPresent(metadata, "credentialKind", runtimeMetadata.credentialKind());
        metadata.put("badges", runtimeMetadata.badges());
        return metadata;
    }

    private Map<String, Object> mergeValidationMetadata(
            final Map<String, Object> currentMetadata,
            final Map<String, Object> validationMetadata) {
        final Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (currentMetadata != null && !currentMetadata.isEmpty()) {
            mergedMetadata.putAll(currentMetadata);
        }
        VALIDATION_METADATA_KEYS.forEach(mergedMetadata::remove);
        if (validationMetadata != null && !validationMetadata.isEmpty()) {
            mergedMetadata.putAll(validationMetadata);
        }
        return mergedMetadata.isEmpty() ? Map.of() : Map.copyOf(mergedMetadata);
    }

    private Map<String, Object> withoutValidationMetadata(final Map<String, Object> currentMetadata) {
        return mergeValidationMetadata(currentMetadata, Map.of());
    }

    private List<DiscoveredHeavyModel> readDiscoveredModels(final UserAiConnectionEntity entity) {
        if (entity.getMetadata() == null) {
            return List.of();
        }

        final Object rawDiscoveredModels = entity.getMetadata().get("discoveredModels");
        if (rawDiscoveredModels == null) {
            return List.of();
        }

        final List<DiscoveredHeavyModel> discoveredModels = objectMapper.convertValue(rawDiscoveredModels, DISCOVERED_MODEL_LIST_TYPE);
        if (discoveredModels == null || discoveredModels.isEmpty()) {
            return List.of();
        }
        return discoveredModels.stream()
                .map(model -> enrichDiscoveredModel(entity, model))
                .toList();
    }

    private boolean isVisibleByokConnection(final UserAiConnectionEntity entity) {
        return entity.getBillingMode() == AiConnectionBillingMode.BYOK
                && entity.getAuthMethod() == AiConnectionAuthMethod.API_KEY
                && ProductAiProviderRegistry.isVisible(entity.getProviderKey(), aiProviderRuntimeProperties.testModeEnabled());
    }

    private boolean isVisibleUserLinkedConnection(final UserAiConnectionEntity entity) {
        return entity.getBillingMode() == AiConnectionBillingMode.USER_LINKED
                && entity.getAuthMethod() == AiConnectionAuthMethod.OAUTH
                && ProductAiProviderRegistry.findConnectionSurface(entity.getConnectionType())
                .filter(surface -> !surface.testOnly() || aiProviderRuntimeProperties.testModeEnabled())
                .isPresent();
    }

    private boolean isVisibleConnection(final UserAiConnectionEntity entity) {
        return isVisibleByokConnection(entity) || isVisibleUserLinkedConnection(entity);
    }

    private boolean isRuntimeSyncRequired(final UserAiConnectionEntity entity) {
        return entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(OffsetDateTime.now())
                || entity.getMetadata() == null
                || !entity.getMetadata().containsKey("discoveredModels");
    }

    private boolean isValidByokConnection(final UserAiConnectionEntity entity) {
        return isVisibleByokConnection(entity) && entity.getConnectionStatus() == AiConnectionStatus.VALID;
    }

    private ProviderCredential toProviderCredential(final UserAiConnectionEntity entity, final UUID userId) {
        return new ProviderCredential(
                entity.getProviderKey(),
                secretEncryptionService.decrypt(new EncryptedSecret(entity.getEncryptedApiKey(), entity.getApiKeyKeyVersion())),
                credentialSource(entity),
                userId.toString(),
                entity.getId(),
                entity.getConnectionType());
    }

    private DiscoveredHeavyModel enrichDiscoveredModel(
            final UserAiConnectionEntity entity,
            final DiscoveredHeavyModel model) {
        final UUID selectedConnectionId = model.selectedConnectionId() != null ? model.selectedConnectionId() : entity.getId();
        final String credentialOwner = model.credentialOwner() != null && !model.credentialOwner().isBlank()
                ? model.credentialOwner()
                : entity.getUserId().toString();
        final AiConnectionExecutionClass executionClass = model.executionClass() != null
                ? model.executionClass()
                : defaultExecutionClass(model.source());
        return new DiscoveredHeavyModel(
                model.provider(),
                model.family(),
                model.concreteModelId(),
                model.displayName(),
                model.source(),
                credentialOwner,
                selectedConnectionId,
                executionClass,
                model.connectionType() != null ? model.connectionType() : entity.getConnectionType());
    }

    private CredentialSource credentialSource(final UserAiConnectionEntity entity) {
        return switch (entity.getBillingMode()) {
            case PLATFORM_PAID -> CredentialSource.PLATFORM;
            case BYOK -> CredentialSource.BYOK;
            case USER_LINKED -> CredentialSource.USER_LINKED;
        };
    }

    private AiConnectionExecutionClass defaultExecutionClass(final CredentialSource source) {
        return source == CredentialSource.PLATFORM
                ? AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE
                : AiConnectionExecutionClass.USER_SINGLE_ROUTE;
    }

    private ProviderCredentialStatusResponse toStatusResponse(final UserAiConnectionEntity entity) {
        final ProductAiProviderRegistry.ProductAiConnectionSurface connectionSurface = ProductAiProviderRegistry
                .findConnectionSurface(entity.getConnectionType())
                .orElse(null);
        final Optional<ProductAiProviderRegistry.ProductAiProvider> providerDefinitionOptional = ProductAiProviderRegistry.find(entity.getProviderKey());
        final ProductAiProviderRegistry.ProductAiProvider providerDefinition = providerDefinitionOptional
                .orElse(new ProductAiProviderRegistry.ProductAiProvider(
                        entity.getProviderKey(),
                        entity.getProviderKey(),
                        null,
                        Integer.MAX_VALUE,
                        false,
                        false,
                        List.of()));
        final Map<String, Object> metadata = entity.getMetadata() == null ? Map.of() : entity.getMetadata();
        final ProviderRuntimeMetadata fallbackRuntimeMetadata = providerRuntimeMetadataResolver.resolveForProvider(entity.getProviderKey());
        final boolean preferSurfaceDefinition = entity.getBillingMode() == AiConnectionBillingMode.USER_LINKED || providerDefinitionOptional.isEmpty();
        final String displayName = preferSurfaceDefinition && connectionSurface != null
                ? connectionSurface.displayName()
                : providerDefinition.displayName();
        final boolean fallbackTestOnly = fallbackRuntimeMetadata.testOnly()
                || providerDefinition.testOnly()
                || (preferSurfaceDefinition && connectionSurface != null && connectionSurface.testOnly());
        final boolean fallbackEmulated = fallbackRuntimeMetadata.emulated()
                || providerDefinition.emulated()
                || (preferSurfaceDefinition && connectionSurface != null && connectionSurface.emulated());
        final List<String> fallbackBadges = fallbackRuntimeMetadata.badges().isEmpty()
                ? preferSurfaceDefinition && connectionSurface != null ? connectionSurface.badges() : providerDefinition.badges()
                : fallbackRuntimeMetadata.badges();
        return new ProviderCredentialStatusResponse(
                entity.getProviderKey(),
                toProviderCredentialStatus(entity.getConnectionStatus()),
                entity.getApiKeyHint(),
                entity.getLastValidatedAt(),
                displayName,
                readBoolean(metadata, "testOnly", fallbackTestOnly),
                readBoolean(metadata, "emulated", fallbackEmulated),
                readString(metadata, "gateway", fallbackRuntimeMetadata.gateway()),
                readString(metadata, "runtimeProvider", fallbackRuntimeMetadata.runtimeProvider()),
                readString(metadata, "runtimeTarget", fallbackRuntimeMetadata.runtimeTarget()),
                readString(metadata, "credentialKind", fallbackRuntimeMetadata.credentialKind()),
                entity.getId(),
                entity.getConnectionType(),
                entity.getConnectionStatus(),
                entity.getAuthMethod(),
                entity.getBillingMode(),
                readStringList(metadata, "badges", fallbackBadges),
                requiresReauth(entity),
                readOffsetDateTime(metadata, "catalogRefreshedAt"),
                null,
                null,
                null,
                null);
    }

    private int displayOrderOf(final UserAiConnectionEntity entity) {
        return ProductAiProviderRegistry.findConnectionSurface(entity.getConnectionType())
                .map(ProductAiProviderRegistry.ProductAiConnectionSurface::displayOrder)
                .orElse(ApprovedHeavyModelRegistry.providerOrder(entity.getProviderKey()));
    }

    private ProviderCredentialStatus toProviderCredentialStatus(final AiConnectionStatus connectionStatus) {
        return switch (connectionStatus) {
            case PENDING -> ProviderCredentialStatus.PENDING;
            case VALID -> ProviderCredentialStatus.VALID;
            case REVOKED -> ProviderCredentialStatus.REVOKED;
            case INVALID, EXPIRED, REAUTH_REQUIRED, ENTITLEMENT_MISSING, DEGRADED -> ProviderCredentialStatus.INVALID;
        };
    }

    private String readString(final Map<String, Object> metadata, final String key, final String fallback) {
        final Object value = metadata.get(key);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : fallback;
    }

    private boolean readBoolean(final Map<String, Object> metadata, final String key, final boolean fallback) {
        final Object value = metadata.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private List<String> readStringList(final Map<String, Object> metadata, final String key, final List<String> fallback) {
        final Object value = metadata.get(key);
        if (value instanceof List<?> rawList) {
            return rawList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return fallback == null ? List.of() : fallback;
    }

    private OffsetDateTime readOffsetDateTime(final Map<String, Object> metadata, final String key) {
        final Object value = metadata.get(key);
        if (!(value instanceof String textValue) || textValue.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(textValue);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean requiresReauth(final UserAiConnectionEntity entity) {
        return entity.getReauthRequiredAt() != null
                || entity.getConnectionStatus() == AiConnectionStatus.REAUTH_REQUIRED
                || entity.getConnectionStatus() == AiConnectionStatus.EXPIRED;
    }

    private void putIfPresent(final Map<String, Object> metadata, final String key, final String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }
}
