package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.GitHubAiConnectionService;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackRequest;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackResponse;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCompletionRequest;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthStartResponse;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GitHubAiConnectionManager implements GitHubAiConnectionService {

    private static final String PROVIDER_KEY = "github";

    private final UserAiConnectionRepository userAiConnectionRepository;
    private final GitHubAiOAuthApiClient gitHubAiOAuthApiClient;
    private final GitHubAiOAuthStateService gitHubAiOAuthStateService;
    private final GitHubAiOAuthCompletionTokenService gitHubAiOAuthCompletionTokenService;
    private final UserLinkedConnectionRuntimeManager userLinkedConnectionRuntimeManager;
    private final ProviderCredentialManager providerCredentialManager;

    public GitHubAiConnectionManager(
            final UserAiConnectionRepository userAiConnectionRepository,
            final GitHubAiOAuthApiClient gitHubAiOAuthApiClient,
            final GitHubAiOAuthStateService gitHubAiOAuthStateService,
            final GitHubAiOAuthCompletionTokenService gitHubAiOAuthCompletionTokenService,
            final UserLinkedConnectionRuntimeManager userLinkedConnectionRuntimeManager,
            final ProviderCredentialManager providerCredentialManager) {
        this.userAiConnectionRepository = userAiConnectionRepository;
        this.gitHubAiOAuthApiClient = gitHubAiOAuthApiClient;
        this.gitHubAiOAuthStateService = gitHubAiOAuthStateService;
        this.gitHubAiOAuthCompletionTokenService = gitHubAiOAuthCompletionTokenService;
        this.userLinkedConnectionRuntimeManager = userLinkedConnectionRuntimeManager;
        this.providerCredentialManager = providerCredentialManager;
    }

    @Override
    @Transactional
    public GitHubAiOAuthStartResponse startAuthorization(final UUID userId, final String surface) {
        gitHubAiOAuthApiClient.ensureConfigured();

        final GitHubAiConnectionProduct product = GitHubAiConnectionProduct.fromSurface(surface);
        final GitHubAiOAuthState state = gitHubAiOAuthStateService.issue(userId, product);
        final String stateToken = gitHubAiOAuthStateService.encode(state);

        final UserAiConnectionEntity entity = userAiConnectionRepository
                .findByUserIdAndConnectionType(userId, product.connectionType())
                .orElseGet(() -> UserAiConnectionEntity.createPendingOauth(userId, product.connectionType(), PROVIDER_KEY));
        entity.markOauthAuthorizationPending(metadataForAuthorizationStart(entity.getMetadata(), product, state));
        userAiConnectionRepository.save(entity);

        return new GitHubAiOAuthStartResponse(
                PROVIDER_KEY,
                product.surfaceKey(),
                product.connectionType(),
                gitHubAiOAuthApiClient.buildAuthorizationUrl(product, stateToken),
                stateToken,
                state.expiresAt());
    }

    @Override
    public GitHubAiOAuthCallbackResponse handleCallback(final GitHubAiOAuthCallbackRequest request) {
        gitHubAiOAuthApiClient.ensureConfigured();
        if (request == null) {
            throw new IllegalArgumentException("GitHub AI callback payload bos olamaz.");
        }
        if (request.error() != null && !request.error().isBlank()) {
            throw new IllegalArgumentException(request.errorDescription() == null || request.errorDescription().isBlank()
                    ? "GitHub AI authorization basarisiz: " + request.error()
                    : request.errorDescription());
        }
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("GitHub AI authorization code bos olamaz.");
        }

        final GitHubAiOAuthState state = gitHubAiOAuthStateService.decode(request.state());
        final GitHubAiConnectionProduct product = GitHubAiConnectionProduct.fromSurface(state.surface());
        if (product.connectionType() != state.connectionType()) {
            throw new IllegalArgumentException("GitHub AI state surface/type uyumsuz.");
        }

        final com.arslanca.orchestrai.business.dtos.auth.GitHubTokenResponse tokenResponse = gitHubAiOAuthApiClient.exchangeCodeForToken(request.code());
        final com.arslanca.orchestrai.business.dtos.auth.GitHubUserInfo userInfo = gitHubAiOAuthApiClient.fetchUserInfo(tokenResponse.accessToken());
        final OffsetDateTime authorizedAt = OffsetDateTime.now(ZoneOffset.UTC);
        final OffsetDateTime tokenExpiresAt = expiresAtFrom(tokenResponse, authorizedAt);
        final String providerAccountSubject = String.valueOf(userInfo.id());
        final String providerAccountLabel = accountLabelOf(userInfo, providerAccountSubject);
        final GitHubAiOAuthCompletionPayload payload = new GitHubAiOAuthCompletionPayload(
                state.userId(),
                state.connectionType(),
                state.surface(),
                state.stateId(),
                providerAccountSubject,
                providerAccountLabel,
                tokenResponse.accessToken(),
                blankToNull(tokenResponse.refreshToken()),
                blankToNull(tokenResponse.tokenType()),
                blankToNull(tokenResponse.scope()),
                parseScopes(tokenResponse.scope()),
                tokenExpiresAt == null ? null : tokenExpiresAt.toString(),
                authorizedAt.toString(),
                state.expiresAt().toString());

        return new GitHubAiOAuthCallbackResponse(
                PROVIDER_KEY,
                product.surfaceKey(),
                product.connectionType(),
                gitHubAiOAuthCompletionTokenService.issue(payload),
                providerAccountSubject,
                providerAccountLabel,
                state.expiresAt());
    }

    @Override
    @Transactional
    public ProviderCredentialStatusResponse completeAuthorization(
            final UUID userId,
            final GitHubAiOAuthCompletionRequest request) {
        final GitHubAiOAuthCompletionPayload payload = gitHubAiOAuthCompletionTokenService.decode(request.completionToken());
        if (!userId.equals(payload.userId())) {
            throw new IllegalArgumentException("Completion token kullanicisi ile aktif session eslesmiyor.");
        }

        final GitHubAiConnectionProduct product = GitHubAiConnectionProduct.fromSurface(payload.surface());
        if (product.connectionType() != payload.connectionType()) {
            throw new IllegalArgumentException("Completion token surface/type uyumsuz.");
        }

        final UserAiConnectionEntity entity = userAiConnectionRepository
                .findByUserIdAndConnectionType(userId, payload.connectionType())
                .orElseGet(() -> UserAiConnectionEntity.createPendingOauth(userId, payload.connectionType(), PROVIDER_KEY));
        verifyStateConsistency(entity, payload.stateId());

        userLinkedConnectionRuntimeManager.connectAndValidate(
                userId,
                payload.connectionType(),
                payload.accessToken(),
                payload.refreshToken(),
                parseOffsetDateTime(payload.tokenExpiresAt()),
                payload.providerAccountSubject(),
                payload.providerAccountLabel(),
                metadataForAuthorizationComplete(entity.getMetadata(), product, payload));

        return providerCredentialManager.findConnectionStatus(userId, payload.connectionType())
                .orElseThrow(() -> new IllegalStateException("GitHub AI connection status olusturulamadi."));
    }

    @Override
    @Transactional
    public void revokeConnection(final UUID userId, final String surface) {
        final GitHubAiConnectionProduct product = GitHubAiConnectionProduct.fromSurface(surface);
        userAiConnectionRepository.findByUserIdAndConnectionType(userId, product.connectionType())
                .ifPresent(entity -> {
                    entity.markRevoked(metadataForRevocation(entity.getMetadata(), product));
                    userAiConnectionRepository.save(entity);
                });
    }

    private Map<String, Object> metadataForAuthorizationStart(
            final Map<String, Object> currentMetadata,
            final GitHubAiConnectionProduct product,
            final GitHubAiOAuthState state) {
        final Map<String, Object> metadata = copyMetadata(currentMetadata);
        metadata.put("runtimeProvider", PROVIDER_KEY);
        metadata.put("runtimeTarget", product.runtimeTarget());
        metadata.put("credentialKind", "OAUTH_USER_LINKED");
        metadata.put("badges", List.of("USER_LINKED", "OAUTH", product.connectionType().name()));
        metadata.put("oauthState", Map.of(
                "provider", PROVIDER_KEY,
                "surface", product.surfaceKey(),
                "connectionType", product.connectionType().name(),
                "stateId", state.stateId(),
                "issuedAt", state.issuedAt().toString(),
                "expiresAt", state.expiresAt().toString(),
                "redirectUri", gitHubAiOAuthApiClient.redirectUri()
        ));
        metadata.put("oauthRequestedScopes", gitHubAiOAuthApiClient.scopesFor(product));
        metadata.put("oauthAuthorizationStatus", "PENDING");
        metadata.put("oauthLastStartedAt", state.issuedAt().toString());
        return immutableMetadata(metadata);
    }

    private Map<String, Object> metadataForAuthorizationComplete(
            final Map<String, Object> currentMetadata,
            final GitHubAiConnectionProduct product,
            final GitHubAiOAuthCompletionPayload payload) {
        final Map<String, Object> metadata = copyMetadata(currentMetadata);
        metadata.put("runtimeProvider", PROVIDER_KEY);
        metadata.put("runtimeTarget", product.runtimeTarget());
        metadata.put("credentialKind", "OAUTH_USER_LINKED");
        metadata.put("badges", List.of("USER_LINKED", "OAUTH", product.connectionType().name()));
        metadata.put("oauthAuthorizationStatus", "CONNECTED");
        metadata.put("oauthAuthorizedAt", payload.authorizedAt());
        metadata.put("oauthTokenType", payload.tokenType());
        metadata.put("oauthScope", payload.scope());
        metadata.put("oauthScopes", payload.scopes());
        metadata.put("oauthRefreshTokenPresent", payload.refreshToken() != null);
        metadata.put("providerAccountSubject", payload.providerAccountSubject());
        metadata.put("providerAccountLabel", payload.providerAccountLabel());
        metadata.put("oauthState", Map.of(
                "provider", PROVIDER_KEY,
                "surface", product.surfaceKey(),
                "connectionType", product.connectionType().name(),
                "stateId", payload.stateId(),
                "completedAt", payload.authorizedAt()
        ));
        return immutableMetadata(metadata);
    }

    private Map<String, Object> metadataForRevocation(
            final Map<String, Object> currentMetadata,
            final GitHubAiConnectionProduct product) {
        final Map<String, Object> metadata = copyMetadata(currentMetadata);
        metadata.put("runtimeProvider", PROVIDER_KEY);
        metadata.put("runtimeTarget", product.runtimeTarget());
        metadata.put("credentialKind", "OAUTH_USER_LINKED");
        metadata.put("badges", List.of("USER_LINKED", "OAUTH", product.connectionType().name()));
        metadata.put("oauthAuthorizationStatus", "REVOKED");
        metadata.put("oauthRevokedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return immutableMetadata(metadata);
    }

    private void verifyStateConsistency(final UserAiConnectionEntity entity, final String stateId) {
        if (entity.getMetadata() == null) {
            return;
        }
        final Object rawOauthState = entity.getMetadata().get("oauthState");
        if (!(rawOauthState instanceof Map<?, ?> oauthStateMetadata)) {
            return;
        }
        final Object currentStateId = oauthStateMetadata.get("stateId");
        if (currentStateId instanceof String storedStateId
                && !storedStateId.isBlank()
                && !storedStateId.equals(stateId)) {
            entity.markOauthReauthRequired(immutableMetadata(copyMetadata(entity.getMetadata())));
            userAiConnectionRepository.save(entity);
            throw new IllegalStateException("GitHub AI authorization state yenilenmis. En son authorize akisini tekrar deneyin.");
        }
    }

    private OffsetDateTime expiresAtFrom(final com.arslanca.orchestrai.business.dtos.auth.GitHubTokenResponse tokenResponse, final OffsetDateTime issuedAt) {
        return tokenResponse.expiresIn() == null || tokenResponse.expiresIn() <= 0
                ? null
                : issuedAt.plusSeconds(tokenResponse.expiresIn());
    }

    private OffsetDateTime parseOffsetDateTime(final String value) {
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
    }

    private String accountLabelOf(final com.arslanca.orchestrai.business.dtos.auth.GitHubUserInfo userInfo, final String providerAccountSubject) {
        if (userInfo.login() != null && !userInfo.login().isBlank()) {
            return userInfo.login();
        }
        if (userInfo.email() != null && !userInfo.email().isBlank()) {
            return userInfo.email();
        }
        return providerAccountSubject;
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> parseScopes(final String rawScopes) {
        if (rawScopes == null || rawScopes.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(rawScopes.split("[\\s,]+"))
                .filter(scope -> scope != null && !scope.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> copyMetadata(final Map<String, Object> currentMetadata) {
        final Map<String, Object> metadata = new LinkedHashMap<>();
        if (currentMetadata != null && !currentMetadata.isEmpty()) {
            metadata.putAll(currentMetadata);
        }
        return metadata;
    }

    private Map<String, Object> immutableMetadata(final Map<String, Object> metadata) {
        return metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }
}
