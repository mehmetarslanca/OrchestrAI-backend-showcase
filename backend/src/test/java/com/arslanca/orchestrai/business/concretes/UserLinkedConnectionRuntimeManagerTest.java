package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.auth.GitHubTokenResponse;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.core.security.EncryptedSecret;
import com.arslanca.orchestrai.core.security.SecretEncryptionService;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLinkedConnectionRuntimeManagerTest {

    @Mock
    private UserAiConnectionRepository userAiConnectionRepository;

    @Mock
    private SecretEncryptionService secretEncryptionService;

    @Mock
    private GitHubAiOAuthApiClient gitHubAiOAuthApiClient;

    @Mock
    private HeavyProviderAdapter gitHubModelsAdapter;

    @Test
    void shouldResolveValidUserLinkedCredentialAndRefreshDiscoveredModels() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingOauth(userId, AiConnectionType.GITHUB_MODELS_USER_LINKED, "github");
        ReflectionTestUtils.setField(entity, "id", connectionId);
        entity.markOauthConnected(
                "enc-access",
                1,
                "enc-refresh",
                1,
                OffsetDateTime.now().plusMinutes(30),
                "12345",
                "octocat",
                Map.of());
        when(userAiConnectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(entity));
        when(secretEncryptionService.decrypt(new EncryptedSecret("enc-access", 1))).thenReturn("access-token");
        when(secretEncryptionService.decrypt(new EncryptedSecret("enc-refresh", 1))).thenReturn("refresh-token");
        when(gitHubModelsAdapter.supports(any(ProviderCredential.class))).thenReturn(true);
        when(gitHubModelsAdapter.listAvailableHeavyModels(any())).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "github",
                        "gpt-4.1",
                        "openai/gpt-4.1",
                        "GPT-4.1",
                        CredentialSource.USER_LINKED,
                        userId.toString(),
                        connectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                        AiConnectionType.GITHUB_MODELS_USER_LINKED)));
        when(userAiConnectionRepository.save(any(UserAiConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserLinkedConnectionRuntimeManager manager = new UserLinkedConnectionRuntimeManager(
                userAiConnectionRepository,
                secretEncryptionService,
                gitHubAiOAuthApiClient,
                List.of(gitHubModelsAdapter));

        Optional<ProviderCredential> credential = manager.resolveValidCredential(userId, connectionId);

        assertThat(credential).isPresent();
        assertThat(credential.get().connectionType()).isEqualTo(AiConnectionType.GITHUB_MODELS_USER_LINKED);
        ArgumentCaptor<UserAiConnectionEntity> captor = ArgumentCaptor.forClass(UserAiConnectionEntity.class);
        verify(userAiConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).containsKey("discoveredModels");
    }

    @Test
    void shouldMarkReauthRequiredWhenExpiredConnectionHasNoRefreshToken() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingOauth(userId, AiConnectionType.GITHUB_MODELS_USER_LINKED, "github");
        ReflectionTestUtils.setField(entity, "id", connectionId);
        entity.markOauthConnected(
                "enc-access",
                1,
                null,
                null,
                OffsetDateTime.now().minusMinutes(5),
                "12345",
                "octocat",
                Map.of());
        when(userAiConnectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(entity));
        when(secretEncryptionService.decrypt(new EncryptedSecret("enc-access", 1))).thenReturn("access-token");
        when(userAiConnectionRepository.save(any(UserAiConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserLinkedConnectionRuntimeManager manager = new UserLinkedConnectionRuntimeManager(
                userAiConnectionRepository,
                secretEncryptionService,
                gitHubAiOAuthApiClient,
                List.of(gitHubModelsAdapter));

        Optional<ProviderCredential> credential = manager.resolveValidCredential(userId, connectionId);

        assertThat(credential).isEmpty();
        ArgumentCaptor<UserAiConnectionEntity> captor = ArgumentCaptor.forClass(UserAiConnectionEntity.class);
        verify(userAiConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getConnectionStatus()).isEqualTo(AiConnectionStatus.REAUTH_REQUIRED);
    }
}
