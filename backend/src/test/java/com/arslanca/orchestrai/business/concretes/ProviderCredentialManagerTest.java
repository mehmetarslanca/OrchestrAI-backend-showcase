package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadataResolver;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.arslanca.orchestrai.core.security.EncryptedSecret;
import com.arslanca.orchestrai.core.security.SecretEncryptionService;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderCredentialManagerTest {

    @Mock
    private UserAiConnectionRepository userAiConnectionRepository;

    @Mock
    private SecretEncryptionService secretEncryptionService;

    @Mock
    private UserLinkedConnectionRuntimeManager userLinkedConnectionRuntimeManager;

    @Mock
    private HeavyProviderAdapter openAiAdapter;

    @Test
    void shouldPersistValidCredentialAndMaskSecret() {
        UUID userId = UUID.randomUUID();
        when(openAiAdapter.providerKey()).thenReturn("openai");
        when(secretEncryptionService.encrypt("sk-openai-12345678")).thenReturn(new EncryptedSecret("cipher", 7));
        when(secretEncryptionService.currentKeyVersion()).thenReturn(7);
        when(userAiConnectionRepository.findByUserIdAndConnectionType(userId, AiConnectionType.OPENAI_BYOK)).thenReturn(Optional.empty());
        when(userAiConnectionRepository.save(any(UserAiConnectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(openAiAdapter.listAvailableHeavyModels(any())).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4",
                        "GPT-5.4",
                        CredentialSource.BYOK,
                        userId.toString())));

        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        ProviderCredentialStatusResponse response = manager.saveCredential(userId, "openai", "sk-openai-12345678");

        ArgumentCaptor<UserAiConnectionEntity> captor = ArgumentCaptor.forClass(UserAiConnectionEntity.class);
        verify(userAiConnectionRepository, times(2)).save(captor.capture());
        UserAiConnectionEntity latest = captor.getAllValues().get(1);

        assertThat(response.status()).isEqualTo(ProviderCredentialStatus.VALID);
        assertThat(response.displayName()).isEqualTo("OpenAI");
        assertThat(response.maskedHint()).isEqualTo("sk-o...5678");
        assertThat(latest.getEncryptedApiKey()).isEqualTo("cipher");
        assertThat(latest.getConnectionStatus()).isEqualTo(AiConnectionStatus.VALID);
    }

    @Test
    void shouldPersistInvalidCredentialStatusOnUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(openAiAdapter.providerKey()).thenReturn("openai");
        when(secretEncryptionService.encrypt("sk-openai-12345678")).thenReturn(new EncryptedSecret("cipher", 1));
        when(secretEncryptionService.currentKeyVersion()).thenReturn(1);
        when(userAiConnectionRepository.findByUserIdAndConnectionType(userId, AiConnectionType.OPENAI_BYOK)).thenReturn(Optional.empty());
        when(userAiConnectionRepository.save(any(UserAiConnectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(openAiAdapter.listAvailableHeavyModels(any())).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        ProviderCredentialStatusResponse response = manager.saveCredential(userId, "openai", "sk-openai-12345678");

        assertThat(response.status()).isEqualTo(ProviderCredentialStatus.INVALID);
    }

    @Test
    void shouldFailForTransientProviderErrors() {
        UUID userId = UUID.randomUUID();
        when(openAiAdapter.providerKey()).thenReturn("openai");
        when(secretEncryptionService.encrypt("sk-openai-12345678")).thenReturn(new EncryptedSecret("cipher", 1));
        when(secretEncryptionService.currentKeyVersion()).thenReturn(1);
        when(userAiConnectionRepository.findByUserIdAndConnectionType(userId, AiConnectionType.OPENAI_BYOK)).thenReturn(Optional.empty());
        when(userAiConnectionRepository.save(any(UserAiConnectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(openAiAdapter.listAvailableHeavyModels(any())).thenThrow(new RuntimeException("timeout"));

        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        assertThatThrownBy(() -> manager.saveCredential(userId, "openai", "sk-openai-12345678"))
                .isInstanceOf(InfrastructureUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");

        ArgumentCaptor<UserAiConnectionEntity> captor = ArgumentCaptor.forClass(UserAiConnectionEntity.class);
        verify(userAiConnectionRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getConnectionStatus()).isEqualTo(AiConnectionStatus.PENDING);
    }

    @Test
    void shouldRejectOversizedApiKey() {
        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        assertThatThrownBy(() -> manager.saveCredential(UUID.randomUUID(), "openai", "x".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en fazla 1000 karakter");
    }

    @Test
    void shouldRejectTestOnlyProviderWhenTestModeIsDisabled() {
        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        assertThatThrownBy(() -> manager.saveCredential(UUID.randomUUID(), "ollama", "sk-local-test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aktif degil");
    }

    @Test
    void shouldResolveValidCredentialBySelectedConnectionId() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingByok(userId, AiConnectionType.OPENAI_BYOK, "openai", 3);
        ReflectionTestUtils.setField(entity, "id", connectionId);
        entity.markPending("cipher", "sk-o...5678", 3, Map.of());
        entity.markValid(Map.of());

        when(userAiConnectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(entity));
        when(secretEncryptionService.decrypt(new EncryptedSecret("cipher", 3))).thenReturn("sk-openai-12345678");

        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        Optional<com.arslanca.orchestrai.core.ai.provider.ProviderCredential> credential = manager.findValidCredential(userId, connectionId);

        assertThat(credential).isPresent();
        assertThat(credential.get().selectedConnectionId()).isEqualTo(connectionId);
        assertThat(credential.get().source()).isEqualTo(CredentialSource.BYOK);
    }

    @Test
    void shouldBackfillSelectedConnectionOwnerAndExecutionClassFromConnectionMetadata() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingByok(userId, AiConnectionType.OPENAI_BYOK, "openai", 3);
        ReflectionTestUtils.setField(entity, "id", connectionId);
        entity.markPending("cipher", "sk-o...5678", 3, Map.of());
        entity.markValid(Map.of(
                "discoveredModels",
                List.of(Map.of(
                        "provider", "openai",
                        "family", "gpt-5.4",
                        "concreteModelId", "gpt-5.4",
                        "displayName", "GPT-5.4",
                        "source", CredentialSource.BYOK.name()))));

        when(userAiConnectionRepository.findAllByUserIdAndConnectionStatusIn(userId, java.util.EnumSet.of(AiConnectionStatus.VALID)))
                .thenReturn(List.of(entity));

        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        List<DiscoveredHeavyModel> models = manager.getAvailableModels(userId);

        assertThat(models).singleElement().satisfies(model -> {
            assertThat(model.selectedConnectionId()).isEqualTo(connectionId);
            assertThat(model.credentialOwner()).isEqualTo(userId.toString());
            assertThat(model.executionClass()).isEqualTo(AiConnectionExecutionClass.USER_SINGLE_ROUTE);
        });
    }

    @Test
    void shouldPreserveNonValidationMetadataWhenRefreshingExistingCredential() {
        UUID userId = UUID.randomUUID();
        when(openAiAdapter.providerKey()).thenReturn("openai");
        when(secretEncryptionService.encrypt("sk-openai-12345678")).thenReturn(new EncryptedSecret("cipher", 7));
        when(userAiConnectionRepository.save(any(UserAiConnectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(openAiAdapter.listAvailableHeavyModels(any())).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4",
                        "GPT-5.4",
                        CredentialSource.BYOK,
                        userId.toString())));

        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingByok(userId, AiConnectionType.OPENAI_BYOK, "openai", 1);
        Map<String, Object> staleMetadata = new LinkedHashMap<>();
        staleMetadata.put("providerAccountLabel", "Acme Workspace");
        staleMetadata.put("gateway", "stale-gateway");
        staleMetadata.put("discoveredModels", List.of(Map.of("provider", "openai")));
        entity.markPending("old-cipher", "sk-o...9999", 1, staleMetadata);
        when(userAiConnectionRepository.findByUserIdAndConnectionType(userId, AiConnectionType.OPENAI_BYOK)).thenReturn(Optional.of(entity));

        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        manager.saveCredential(userId, "openai", "sk-openai-12345678");

        ArgumentCaptor<UserAiConnectionEntity> captor = ArgumentCaptor.forClass(UserAiConnectionEntity.class);
        verify(userAiConnectionRepository, times(2)).save(captor.capture());
        UserAiConnectionEntity latest = captor.getAllValues().get(1);

        assertThat(latest.getMetadata())
                .containsEntry("providerAccountLabel", "Acme Workspace")
                .containsKey("discoveredModels")
                .containsKey("catalogRefreshedAt")
                .doesNotContainEntry("gateway", "stale-gateway");
    }

    @Test
    void shouldExposeGithubUserLinkedConnectionStatusSeparatelyFromIdentityGithub() {
        UUID userId = UUID.randomUUID();
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingOauth(
                userId,
                AiConnectionType.GITHUB_MODELS_USER_LINKED,
                "github");
        entity.markOauthConnected(
                "access-cipher",
                3,
                null,
                null,
                OffsetDateTime.now().plusHours(1),
                "12345",
                "octocat",
                Map.of(
                        "runtimeProvider", "github",
                        "runtimeTarget", "github-models",
                        "credentialKind", "OAUTH_USER_LINKED",
                        "badges", List.of("USER_LINKED", "OAUTH", "DEFERRED")));

        when(userAiConnectionRepository.findAllByUserIdAndConnectionStatusIn(userId, java.util.EnumSet.of(
                AiConnectionStatus.PENDING,
                AiConnectionStatus.VALID,
                AiConnectionStatus.INVALID,
                AiConnectionStatus.EXPIRED,
                AiConnectionStatus.REAUTH_REQUIRED,
                AiConnectionStatus.ENTITLEMENT_MISSING,
                AiConnectionStatus.DEGRADED))).thenReturn(List.of(entity));

        ProviderCredentialManager manager = new ProviderCredentialManager(
                userAiConnectionRepository,
                userLinkedConnectionRuntimeManager,
                secretEncryptionService,
                new ObjectMapper(),
                List.of(openAiAdapter),
                runtimeProperties(false),
                runtimeResolver(false));

        List<ProviderCredentialStatusResponse> statuses = manager.getCredentialStatuses(userId);

        assertThat(statuses).singleElement().satisfies(status -> {
            assertThat(status.provider()).isEqualTo("github");
            assertThat(status.displayName()).isEqualTo("GitHub Models");
            assertThat(status.connectionType()).isEqualTo(AiConnectionType.GITHUB_MODELS_USER_LINKED);
            assertThat(status.billingMode()).isEqualTo(com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode.USER_LINKED);
            assertThat(status.authMethod()).isEqualTo(com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod.OAUTH);
        });
    }

    private AiProviderRuntimeProperties runtimeProperties(final boolean testModeEnabled) {
        return new AiProviderRuntimeProperties(
                new AiProviderRuntimeProperties.TestModeProperties(testModeEnabled),
                Map.of());
    }

    private ProviderRuntimeMetadataResolver runtimeResolver(final boolean testModeEnabled) {
        return new ProviderRuntimeMetadataResolver(runtimeProperties(testModeEnabled));
    }
}
