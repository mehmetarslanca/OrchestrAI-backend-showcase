package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProductAiProviderRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.core.exception.type.GitHubAuthenticationException;
import com.arslanca.orchestrai.core.security.EncryptedSecret;
import com.arslanca.orchestrai.core.security.SecretEncryptionService;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserLinkedConnectionRuntimeManager {

    private final UserAiConnectionRepository userAiConnectionRepository;
    private final SecretEncryptionService secretEncryptionService;
    private final GitHubAiOAuthApiClient gitHubAiOAuthApiClient;
    private final List<HeavyProviderAdapter> heavyProviderAdapters;

    public UserLinkedConnectionRuntimeManager(
            final UserAiConnectionRepository userAiConnectionRepository,
            final SecretEncryptionService secretEncryptionService,
            final GitHubAiOAuthApiClient gitHubAiOAuthApiClient,
            final List<HeavyProviderAdapter> heavyProviderAdapters) {
        this.userAiConnectionRepository = userAiConnectionRepository;
        this.secretEncryptionService = secretEncryptionService;
        this.gitHubAiOAuthApiClient = gitHubAiOAuthApiClient;
        this.heavyProviderAdapters = List.copyOf(heavyProviderAdapters);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ProviderCredential> resolveValidCredential(final UUID userId, final UUID connectionId) {
        return synchronizeConnection(userId, connectionId)
                .filter(entity -> entity.getConnectionStatus() == AiConnectionStatus.VALID)
                .map(this::toProviderCredential);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UserAiConnectionEntity> synchronizeConnection(final UUID userId, final UUID connectionId) {
        return userAiConnectionRepository.findByIdAndUserId(connectionId, userId)
                .map(this::synchronizeConnection);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserAiConnectionEntity connectAndValidate(
            final UUID userId,
            final com.arslanca.orchestrai.entity.enums.AiConnectionType connectionType,
            final String accessToken,
            final String refreshToken,
            final OffsetDateTime expiresAt,
            final String providerAccountSubject,
            final String providerAccountLabel,
            final Map<String, Object> baseMetadata) {
        final UserAiConnectionEntity entity = userAiConnectionRepository
                .findByUserIdAndConnectionType(userId, connectionType)
                .orElseGet(() -> UserAiConnectionEntity.createPendingOauth(userId, connectionType, "github"));
        final EncryptedSecret encryptedAccessToken = secretEncryptionService.encrypt(accessToken);
        final EncryptedSecret encryptedRefreshToken = refreshToken == null || refreshToken.isBlank()
                ? null
                : secretEncryptionService.encrypt(refreshToken);
        return validateAndPersist(
                entity,
                accessToken,
                encryptedAccessToken,
                refreshToken,
                encryptedRefreshToken,
                expiresAt,
                providerAccountSubject,
                providerAccountLabel,
                baseMetadata,
                false);
    }

    private UserAiConnectionEntity synchronizeConnection(final UserAiConnectionEntity entity) {
        if (!isUserLinkedOauthConnection(entity)) {
            return entity;
        }

        final String accessToken = decryptAccessToken(entity);
        if (accessToken == null) {
            entity.markOauthReauthRequired(markRuntimeMetadata(entity.getMetadata(), entity, "reauth-required", null));
            return userAiConnectionRepository.save(entity);
        }

        final RuntimeTokenBundle tokenBundle = refreshIfRequired(entity, accessToken);
        if (tokenBundle.reauthRequired()) {
            entity.markOauthReauthRequired(markRuntimeMetadata(entity.getMetadata(), entity, "reauth-required", tokenBundle.failureReason()));
            return userAiConnectionRepository.save(entity);
        }

        return validateAndPersist(
                entity,
                tokenBundle.accessToken(),
                tokenBundle.encryptedAccessToken(),
                tokenBundle.refreshToken(),
                tokenBundle.encryptedRefreshToken(),
                tokenBundle.expiresAt(),
                entity.getProviderAccountSubject(),
                entity.getProviderAccountLabel(),
                entity.getMetadata(),
                true);
    }

    private RuntimeTokenBundle refreshIfRequired(final UserAiConnectionEntity entity, final String currentAccessToken) {
        if (entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(OffsetDateTime.now())) {
            return RuntimeTokenBundle.active(
                    currentAccessToken,
                    new EncryptedSecret(entity.getEncryptedAccessToken(), entity.getAccessTokenKeyVersion()),
                    decryptRefreshToken(entity),
                    encryptedRefreshToken(entity),
                    entity.getExpiresAt());
        }

        final String refreshToken = decryptRefreshToken(entity);
        if (refreshToken == null) {
            return RuntimeTokenBundle.reauthRequired("refresh-token-missing");
        }

        try {
            final com.arslanca.orchestrai.business.dtos.auth.GitHubTokenResponse refreshedTokens = gitHubAiOAuthApiClient.refreshAccessToken(refreshToken);
            final String effectiveRefreshToken = refreshedTokens.refreshToken() == null || refreshedTokens.refreshToken().isBlank()
                    ? refreshToken
                    : refreshedTokens.refreshToken();
            return RuntimeTokenBundle.active(
                    refreshedTokens.accessToken(),
                    secretEncryptionService.encrypt(refreshedTokens.accessToken()),
                    effectiveRefreshToken,
                    effectiveRefreshToken == null ? null : secretEncryptionService.encrypt(effectiveRefreshToken),
                    refreshedTokens.expiresIn() == null || refreshedTokens.expiresIn() <= 0
                            ? null
                            : OffsetDateTime.now().plusSeconds(refreshedTokens.expiresIn()));
        } catch (final GitHubAuthenticationException ex) {
            return RuntimeTokenBundle.reauthRequired("refresh-failed");
        }
    }

    private UserAiConnectionEntity validateAndPersist(
            final UserAiConnectionEntity entity,
            final String accessToken,
            final EncryptedSecret encryptedAccessToken,
            final String refreshToken,
            final EncryptedSecret encryptedRefreshToken,
            final OffsetDateTime expiresAt,
            final String providerAccountSubject,
            final String providerAccountLabel,
            final Map<String, Object> baseMetadata,
            final boolean runtimeValidation) {
        final ProviderCredential credential = new ProviderCredential(
                entity.getProviderKey(),
                accessToken,
                CredentialSource.USER_LINKED,
                entity.getUserId().toString(),
                entity.getId(),
                entity.getConnectionType());
        final HeavyProviderAdapter adapter = requireAdapter(credential);
        final GitHubAiConnectionProduct product = GitHubAiConnectionProduct.fromConnectionType(entity.getConnectionType());
        try {
            final List<DiscoveredHeavyModel> discoveredModels = adapter.listAvailableHeavyModels(credential);
            if (discoveredModels.isEmpty()) {
                persistFailureState(entity,
                        product.authorizationFailureStatus(),
                        encryptedAccessToken,
                        encryptedRefreshToken,
                        expiresAt,
                        providerAccountSubject,
                        providerAccountLabel,
                        baseMetadata,
                        runtimeValidation ? "runtime-empty-discovery" : "authorization-empty-discovery");
                return userAiConnectionRepository.save(entity);
            }
            entity.markOauthConnected(
                    encryptedAccessToken.payload(),
                    encryptedAccessToken.keyVersion(),
                    encryptedRefreshToken == null ? null : encryptedRefreshToken.payload(),
                    encryptedRefreshToken == null ? null : encryptedRefreshToken.keyVersion(),
                    expiresAt,
                    providerAccountSubject,
                    providerAccountLabel,
                    mergeValidationMetadata(baseMetadata, entity, discoveredModels, "connected", null));
            return userAiConnectionRepository.save(entity);
        } catch (final RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401) {
                entity.markOauthReauthRequired(markRuntimeMetadata(baseMetadata, entity, "reauth-required", "validation-unauthorized"));
                return userAiConnectionRepository.save(entity);
            }
            persistFailureState(entity,
                    product.authorizationFailureStatus(),
                    encryptedAccessToken,
                    encryptedRefreshToken,
                    expiresAt,
                    providerAccountSubject,
                    providerAccountLabel,
                    baseMetadata,
                    "validation-status-" + ex.getStatusCode().value());
            return userAiConnectionRepository.save(entity);
        } catch (final Exception ex) {
            persistFailureState(entity,
                    AiConnectionStatus.DEGRADED,
                    encryptedAccessToken,
                    encryptedRefreshToken,
                    expiresAt,
                    providerAccountSubject,
                    providerAccountLabel,
                    baseMetadata,
                    runtimeValidation ? "runtime-validate-failed" : "authorization-validate-failed");
            return userAiConnectionRepository.save(entity);
        }
    }

    private void persistFailureState(
            final UserAiConnectionEntity entity,
            final AiConnectionStatus failureStatus,
            final EncryptedSecret encryptedAccessToken,
            final EncryptedSecret encryptedRefreshToken,
            final OffsetDateTime expiresAt,
            final String providerAccountSubject,
            final String providerAccountLabel,
            final Map<String, Object> baseMetadata,
            final String reason) {
        final Map<String, Object> metadata = markRuntimeMetadata(baseMetadata, entity, failureStatus.name().toLowerCase(), reason);
        switch (failureStatus) {
            case ENTITLEMENT_MISSING -> entity.markOauthEntitlementMissing(
                    encryptedAccessToken.payload(),
                    encryptedAccessToken.keyVersion(),
                    encryptedRefreshToken == null ? null : encryptedRefreshToken.payload(),
                    encryptedRefreshToken == null ? null : encryptedRefreshToken.keyVersion(),
                    expiresAt,
                    providerAccountSubject,
                    providerAccountLabel,
                    metadata);
            case EXPIRED -> entity.markOauthExpired(
                    encryptedAccessToken.payload(),
                    encryptedAccessToken.keyVersion(),
                    encryptedRefreshToken == null ? null : encryptedRefreshToken.payload(),
                    encryptedRefreshToken == null ? null : encryptedRefreshToken.keyVersion(),
                    expiresAt,
                    providerAccountSubject,
                    providerAccountLabel,
                    metadata);
            default -> entity.markOauthDegraded(
                    encryptedAccessToken.payload(),
                    encryptedAccessToken.keyVersion(),
                    encryptedRefreshToken == null ? null : encryptedRefreshToken.payload(),
                    encryptedRefreshToken == null ? null : encryptedRefreshToken.keyVersion(),
                    expiresAt,
                    providerAccountSubject,
                    providerAccountLabel,
                    metadata);
        }
    }

    private HeavyProviderAdapter requireAdapter(final ProviderCredential credential) {
        return heavyProviderAdapters.stream()
                .filter(adapter -> adapter.supports(credential))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("User-linked AI connection icin uygun adapter bulunamadi."));
    }

    private ProviderCredential toProviderCredential(final UserAiConnectionEntity entity) {
        return new ProviderCredential(
                entity.getProviderKey(),
                secretEncryptionService.decrypt(new EncryptedSecret(entity.getEncryptedAccessToken(), entity.getAccessTokenKeyVersion())),
                CredentialSource.USER_LINKED,
                entity.getUserId().toString(),
                entity.getId(),
                entity.getConnectionType());
    }

    private Map<String, Object> mergeValidationMetadata(
            final Map<String, Object> currentMetadata,
            final UserAiConnectionEntity entity,
            final List<DiscoveredHeavyModel> discoveredModels,
            final String validationState,
            final String validationReason) {
        final Map<String, Object> metadata = copyMetadata(currentMetadata);
        metadata.put("discoveredModels", serializeModels(discoveredModels));
        metadata.put("catalogRefreshedAt", OffsetDateTime.now().toString());
        metadata.put("oauthAuthorizationStatus", validationState.toUpperCase());
        metadata.put("oauthValidationState", validationState);
        if (validationReason != null) {
            metadata.put("oauthValidationReason", validationReason);
        }
        return markRuntimeMetadata(metadata, entity, validationState, validationReason);
    }

    private Map<String, Object> markRuntimeMetadata(
            final Map<String, Object> currentMetadata,
            final UserAiConnectionEntity entity,
            final String validationState,
            final String validationReason) {
        final Map<String, Object> metadata = copyMetadata(currentMetadata);
        final ProductAiProviderRegistry.ProductAiConnectionSurface surface = ProductAiProviderRegistry.findConnectionSurface(entity.getConnectionType())
                .orElseThrow(() -> new IllegalStateException("AI connection surface bulunamadi."));
        metadata.put("runtimeProvider", entity.getProviderKey());
        metadata.put("runtimeTarget", GitHubAiConnectionProduct.fromConnectionType(entity.getConnectionType()).runtimeTarget());
        metadata.put("credentialKind", "OAUTH_USER_LINKED");
        metadata.put("badges", List.of("USER_LINKED", "OAUTH", surface.surfaceKey()));
        metadata.put("oauthValidationState", validationState);
        if (validationReason != null) {
            metadata.put("oauthValidationReason", validationReason);
        }
        putIfPresent(metadata, "providerAccountSubject", entity.getProviderAccountSubject());
        putIfPresent(metadata, "providerAccountLabel", entity.getProviderAccountLabel());
        return metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }

    private List<Map<String, Object>> serializeModels(final List<DiscoveredHeavyModel> discoveredModels) {
        return discoveredModels.stream()
                .map(model -> {
                    final Map<String, Object> serialized = new LinkedHashMap<>();
                    serialized.put("provider", model.provider());
                    serialized.put("family", model.family());
                    serialized.put("concreteModelId", model.concreteModelId());
                    serialized.put("displayName", model.displayName());
                    serialized.put("source", model.source().name());
                    serialized.put("credentialOwner", model.credentialOwner());
                    putIfPresent(serialized, "selectedConnectionId", model.selectedConnectionId() == null ? null : model.selectedConnectionId().toString());
                    putIfPresent(serialized, "executionClass", model.executionClass() == null ? null : model.executionClass().name());
                    putIfPresent(serialized, "connectionType", model.connectionType() == null ? null : model.connectionType().name());
                    return serialized;
                })
                .toList();
    }

    private boolean isUserLinkedOauthConnection(final UserAiConnectionEntity entity) {
        return entity.getAuthMethod() == AiConnectionAuthMethod.OAUTH
                && entity.getBillingMode() == com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode.USER_LINKED;
    }

    private String decryptAccessToken(final UserAiConnectionEntity entity) {
        if (entity.getEncryptedAccessToken() == null || entity.getEncryptedAccessToken().isBlank() || entity.getAccessTokenKeyVersion() == null) {
            return null;
        }
        return secretEncryptionService.decrypt(new EncryptedSecret(entity.getEncryptedAccessToken(), entity.getAccessTokenKeyVersion()));
    }

    private String decryptRefreshToken(final UserAiConnectionEntity entity) {
        if (entity.getEncryptedRefreshToken() == null || entity.getEncryptedRefreshToken().isBlank() || entity.getRefreshTokenKeyVersion() == null) {
            return null;
        }
        return secretEncryptionService.decrypt(new EncryptedSecret(entity.getEncryptedRefreshToken(), entity.getRefreshTokenKeyVersion()));
    }

    private EncryptedSecret encryptedRefreshToken(final UserAiConnectionEntity entity) {
        if (entity.getEncryptedRefreshToken() == null || entity.getRefreshTokenKeyVersion() == null) {
            return null;
        }
        return new EncryptedSecret(entity.getEncryptedRefreshToken(), entity.getRefreshTokenKeyVersion());
    }

    private Map<String, Object> copyMetadata(final Map<String, Object> metadata) {
        final Map<String, Object> copy = new LinkedHashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            copy.putAll(metadata);
        }
        return copy;
    }

    private void putIfPresent(final Map<String, Object> metadata, final String key, final String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private record RuntimeTokenBundle(
            String accessToken,
            EncryptedSecret encryptedAccessToken,
            String refreshToken,
            EncryptedSecret encryptedRefreshToken,
            OffsetDateTime expiresAt,
            boolean reauthRequired,
            String failureReason) {

        private static RuntimeTokenBundle active(
                final String accessToken,
                final EncryptedSecret encryptedAccessToken,
                final String refreshToken,
                final EncryptedSecret encryptedRefreshToken,
                final OffsetDateTime expiresAt) {
            return new RuntimeTokenBundle(accessToken, encryptedAccessToken, refreshToken, encryptedRefreshToken, expiresAt, false, null);
        }

        private static RuntimeTokenBundle reauthRequired(final String failureReason) {
            return new RuntimeTokenBundle(null, null, null, null, null, true, failureReason);
        }
    }
}
