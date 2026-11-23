package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackRequest;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackResponse;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCompletionRequest;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthStartResponse;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.business.dtos.auth.GitHubTokenResponse;
import com.arslanca.orchestrai.business.dtos.auth.GitHubUserInfo;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubAiConnectionManagerTest {

    @Mock
    private UserAiConnectionRepository userAiConnectionRepository;

    @Mock
    private GitHubAiOAuthApiClient gitHubAiOAuthApiClient;

    @Mock
    private GitHubAiOAuthStateService gitHubAiOAuthStateService;

    @Mock
    private GitHubAiOAuthCompletionTokenService gitHubAiOAuthCompletionTokenService;

    @Mock
    private UserLinkedConnectionRuntimeManager userLinkedConnectionRuntimeManager;

    @Mock
    private ProviderCredentialManager providerCredentialManager;

    @Test
    void shouldStartAuthorizationWithDedicatedGithubAiSurface() {
        UUID userId = UUID.randomUUID();
        GitHubAiOAuthState state = new GitHubAiOAuthState(
                userId,
                AiConnectionType.GITHUB_MODELS_USER_LINKED,
                "models",
                "state-123",
                OffsetDateTime.now().minusSeconds(5),
                OffsetDateTime.now().plusMinutes(10));
        when(userAiConnectionRepository.findByUserIdAndConnectionType(userId, AiConnectionType.GITHUB_MODELS_USER_LINKED))
                .thenReturn(Optional.empty());
        when(gitHubAiOAuthStateService.issue(userId, GitHubAiConnectionProduct.MODELS)).thenReturn(state);
        when(gitHubAiOAuthStateService.encode(state)).thenReturn("signed-state");
        when(gitHubAiOAuthApiClient.buildAuthorizationUrl(GitHubAiConnectionProduct.MODELS, "signed-state"))
                .thenReturn("https://github.com/login/oauth/authorize?state=signed-state");
        when(gitHubAiOAuthApiClient.redirectUri()).thenReturn("http://localhost:3000/auth/github-ai/callback");

        GitHubAiConnectionManager manager = new GitHubAiConnectionManager(
                userAiConnectionRepository,
                gitHubAiOAuthApiClient,
                gitHubAiOAuthStateService,
                gitHubAiOAuthCompletionTokenService,
                userLinkedConnectionRuntimeManager,
                providerCredentialManager);

        GitHubAiOAuthStartResponse response = manager.startAuthorization(userId, "models");

        assertThat(response.connectionType()).isEqualTo(AiConnectionType.GITHUB_MODELS_USER_LINKED);
        assertThat(response.provider()).isEqualTo("github");
        assertThat(response.authorizationUrl()).contains("signed-state");

        ArgumentCaptor<UserAiConnectionEntity> captor = ArgumentCaptor.forClass(UserAiConnectionEntity.class);
        verify(userAiConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getBillingMode()).isEqualTo(AiConnectionBillingMode.USER_LINKED);
        assertThat(captor.getValue().getConnectionStatus()).isEqualTo(AiConnectionStatus.PENDING);
        assertThat(captor.getValue().getMetadata()).containsKey("oauthState");
    }

    @Test
    void shouldPreserveExistingOauthSecretsWhenReauthorizationStarts() {
        UUID userId = UUID.randomUUID();
        GitHubAiOAuthState state = new GitHubAiOAuthState(
                userId,
                AiConnectionType.GITHUB_MODELS_USER_LINKED,
                "models",
                "state-keep",
                OffsetDateTime.now().minusSeconds(5),
                OffsetDateTime.now().plusMinutes(10));
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingOauth(
                userId,
                AiConnectionType.GITHUB_MODELS_USER_LINKED,
                "github");
        entity.markOauthConnected(
                "access-cipher",
                2,
                "refresh-cipher",
                3,
                OffsetDateTime.now().plusHours(1),
                "12345",
                "octocat",
                Map.of("runtimeTarget", "github-models"));
        ReflectionTestUtils.setField(entity, "lastValidatedAt", OffsetDateTime.now().minusMinutes(5));

        when(userAiConnectionRepository.findByUserIdAndConnectionType(userId, AiConnectionType.GITHUB_MODELS_USER_LINKED))
                .thenReturn(Optional.of(entity));
        when(gitHubAiOAuthStateService.issue(userId, GitHubAiConnectionProduct.MODELS)).thenReturn(state);
        when(gitHubAiOAuthStateService.encode(state)).thenReturn("signed-state");
        when(gitHubAiOAuthApiClient.buildAuthorizationUrl(GitHubAiConnectionProduct.MODELS, "signed-state"))
                .thenReturn("https://github.com/login/oauth/authorize?state=signed-state");
        when(gitHubAiOAuthApiClient.redirectUri()).thenReturn("http://localhost:3000/auth/github-ai/callback");

        GitHubAiConnectionManager manager = new GitHubAiConnectionManager(
                userAiConnectionRepository,
                gitHubAiOAuthApiClient,
                gitHubAiOAuthStateService,
                gitHubAiOAuthCompletionTokenService,
                userLinkedConnectionRuntimeManager,
                providerCredentialManager);

        manager.startAuthorization(userId, "models");

        ArgumentCaptor<UserAiConnectionEntity> captor = ArgumentCaptor.forClass(UserAiConnectionEntity.class);
        verify(userAiConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getConnectionStatus()).isEqualTo(AiConnectionStatus.VALID);
        assertThat(captor.getValue().getEncryptedAccessToken()).isEqualTo("access-cipher");
        assertThat(captor.getValue().getEncryptedRefreshToken()).isEqualTo("refresh-cipher");
        assertThat(captor.getValue().getMetadata()).containsKey("oauthState");
    }

    @Test
    void shouldCompleteAuthorizationAndPersistEncryptedGithubOauthTokens() {
        UUID userId = UUID.randomUUID();
        GitHubAiOAuthCompletionPayload payload = new GitHubAiOAuthCompletionPayload(
                userId,
                AiConnectionType.GITHUB_COPILOT_USER_LINKED,
                "copilot",
                "state-456",
                "123456",
                "octocat",
                "access-token",
                "refresh-token",
                "bearer",
                "read:user user:email",
                List.of("read:user", "user:email"),
                OffsetDateTime.now().plusHours(1).toString(),
                OffsetDateTime.now().toString(),
                OffsetDateTime.now().plusMinutes(5).toString());
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingOauth(
                userId,
                AiConnectionType.GITHUB_COPILOT_USER_LINKED,
                "github");
        entity.markOauthAuthorizationPending(Map.of("oauthState", Map.of("stateId", "state-456")));

        when(gitHubAiOAuthCompletionTokenService.decode("completion-token")).thenReturn(payload);
        when(userAiConnectionRepository.findByUserIdAndConnectionType(userId, AiConnectionType.GITHUB_COPILOT_USER_LINKED))
                .thenReturn(Optional.of(entity));
        when(userLinkedConnectionRuntimeManager.connectAndValidate(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(AiConnectionType.GITHUB_COPILOT_USER_LINKED),
                org.mockito.ArgumentMatchers.eq("access-token"),
                org.mockito.ArgumentMatchers.eq("refresh-token"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("123456"),
                org.mockito.ArgumentMatchers.eq("octocat"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(entity);
        when(providerCredentialManager.findConnectionStatus(userId, AiConnectionType.GITHUB_COPILOT_USER_LINKED))
                .thenReturn(Optional.of(new ProviderCredentialStatusResponse(
                        "github",
                        ProviderCredentialStatus.VALID,
                        null,
                        OffsetDateTime.now(),
                        "GitHub Copilot",
                        false,
                        false,
                        null,
                        "github",
                        "github-copilot",
                        "OAUTH_USER_LINKED",
                        UUID.randomUUID(),
                        AiConnectionType.GITHUB_COPILOT_USER_LINKED,
                        AiConnectionStatus.VALID,
                        com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod.OAUTH,
                        AiConnectionBillingMode.USER_LINKED,
                        List.of("USER_LINKED", "OAUTH", "DEFERRED"))));

        GitHubAiConnectionManager manager = new GitHubAiConnectionManager(
                userAiConnectionRepository,
                gitHubAiOAuthApiClient,
                gitHubAiOAuthStateService,
                gitHubAiOAuthCompletionTokenService,
                userLinkedConnectionRuntimeManager,
                providerCredentialManager);

        ProviderCredentialStatusResponse response = manager.completeAuthorization(
                userId,
                new GitHubAiOAuthCompletionRequest("completion-token"));

        assertThat(response.connectionType()).isEqualTo(AiConnectionType.GITHUB_COPILOT_USER_LINKED);
        assertThat(response.status()).isEqualTo(ProviderCredentialStatus.VALID);

        verify(userLinkedConnectionRuntimeManager).connectAndValidate(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(AiConnectionType.GITHUB_COPILOT_USER_LINKED),
                org.mockito.ArgumentMatchers.eq("access-token"),
                org.mockito.ArgumentMatchers.eq("refresh-token"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("123456"),
                org.mockito.ArgumentMatchers.eq("octocat"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void shouldCreateCompletionTokenFromCallbackPayload() {
        UUID userId = UUID.randomUUID();
        GitHubAiOAuthState state = new GitHubAiOAuthState(
                userId,
                AiConnectionType.GITHUB_MODELS_USER_LINKED,
                "models",
                "state-789",
                OffsetDateTime.now().minusSeconds(5),
                OffsetDateTime.now().plusMinutes(10));
        when(gitHubAiOAuthStateService.decode("signed-state")).thenReturn(state);
        when(gitHubAiOAuthApiClient.exchangeCodeForToken("oauth-code"))
                .thenReturn(new GitHubTokenResponse("access-token", "refresh-token", 3600L, 7200L, "bearer", "read:user user:email"));
        when(gitHubAiOAuthApiClient.fetchUserInfo("access-token"))
                .thenReturn(new GitHubUserInfo(42L, "octocat", "octo@github.com", null));
        when(gitHubAiOAuthCompletionTokenService.issue(any())).thenReturn("completion-token");

        GitHubAiConnectionManager manager = new GitHubAiConnectionManager(
                userAiConnectionRepository,
                gitHubAiOAuthApiClient,
                gitHubAiOAuthStateService,
                gitHubAiOAuthCompletionTokenService,
                userLinkedConnectionRuntimeManager,
                providerCredentialManager);

        GitHubAiOAuthCallbackResponse response = manager.handleCallback(
                new GitHubAiOAuthCallbackRequest("oauth-code", "signed-state", null, null));

        assertThat(response.surface()).isEqualTo("models");
        assertThat(response.providerAccountLabel()).isEqualTo("octocat");
        assertThat(response.completionToken()).isEqualTo("completion-token");
    }
}
