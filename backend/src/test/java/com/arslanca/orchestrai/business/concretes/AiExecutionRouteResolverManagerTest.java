package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.core.ai.provider.PlatformCredentialRegistry;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExecutionRouteResolverManagerTest {

    @Mock
    private HeavyModelResolutionService heavyModelResolutionService;

    @Mock
    private HeavyModelCredentialResolver heavyModelCredentialResolver;

    @Mock
    private ChatModel lightChatModel;

    @Mock
    private ChatModel heavyChatModel;

    @Mock
    private PlatformCredentialRegistry platformCredentialRegistry;

    @Mock
    private HeavyProviderAdapter heavyProviderAdapter;

    private final AiModelProperties aiModelProperties = new AiModelProperties("gemini-2.5-pro", 0.1d, 2048,
            "openai:gpt-4.1", 0.2d, 8192);

    private final AiProviderRuntimeProperties aiProviderRuntimeProperties = new AiProviderRuntimeProperties(
            new AiProviderRuntimeProperties.TestModeProperties(false),
            java.util.Map.of());

    @Test
    void shouldReturnPlatformDualRouteUsingExistingLightModelBean() {
        UUID userId = UUID.randomUUID();
        ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel(
                "google:gemini-3-pro",
                "google",
                "gemini-3-pro",
                CredentialSource.PLATFORM,
                "platform",
                "gemini-3-pro",
                "Gemini 3 Pro",
                false,
                false,
                null,
                null,
                null,
                java.util.List.of(),
                com.arslanca.orchestrai.business.dtos.ai.ModelCapability.HIGH,
                null,
                AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE,
                null);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);

        AiExecutionRouteResolverManager manager = new AiExecutionRouteResolverManager(
                heavyModelResolutionService,
                heavyModelCredentialResolver,
                lightChatModel,
                aiModelProperties,
                aiProviderRuntimeProperties,
                platformCredentialRegistry);

        ResolvedAiExecutionRoute route = manager.resolve(userId, null, null);

        assertThat(route.executionClass()).isEqualTo(AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE);
        assertThat(route.chatModel()).isEqualTo(lightChatModel);
        assertThat(route.providerCredential()).isNull();
        assertThat(route.runtimeDescriptor()).isNotNull();
        assertThat(route.runtimeDescriptor().providerKey()).isEqualTo("google");
        assertThat(route.runtimeDescriptor().modelKey()).isEqualTo("gemini-2.5-pro");
    }

    @Test
    void shouldReturnUserSingleRouteUsingResolvedCredentialAndAdapter() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel(
                "github:gpt-4.1",
                "github",
                "gpt-4.1",
                CredentialSource.USER_LINKED,
                userId.toString(),
                "openai/gpt-4.1",
                "GPT-4.1",
                false,
                false,
                null,
                "github",
                "github-models",
                java.util.List.of(),
                com.arslanca.orchestrai.business.dtos.ai.ModelCapability.HIGH,
                connectionId,
                AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                AiConnectionType.GITHUB_MODELS_USER_LINKED);
        ProviderCredential providerCredential = new ProviderCredential(
                "github",
                "oauth-token",
                CredentialSource.USER_LINKED,
                userId.toString(),
                connectionId,
                AiConnectionType.GITHUB_MODELS_USER_LINKED);
        when(heavyModelResolutionService.resolve(userId, "github:gpt-4.1", CredentialSource.USER_LINKED)).thenReturn(resolvedHeavyModel);
        when(heavyModelCredentialResolver.resolve(resolvedHeavyModel)).thenReturn(providerCredential);
        when(heavyModelResolutionService.adapterFor(resolvedHeavyModel)).thenReturn(heavyProviderAdapter);
        when(heavyProviderAdapter.buildHeavyChatModel(resolvedHeavyModel, providerCredential)).thenReturn(heavyChatModel);

        AiExecutionRouteResolverManager manager = new AiExecutionRouteResolverManager(
                heavyModelResolutionService,
                heavyModelCredentialResolver,
                lightChatModel,
                aiModelProperties,
                aiProviderRuntimeProperties,
                platformCredentialRegistry);

        ResolvedAiExecutionRoute route = manager.resolve(userId, "github:gpt-4.1", CredentialSource.USER_LINKED);

        assertThat(route.executionClass()).isEqualTo(AiConnectionExecutionClass.USER_SINGLE_ROUTE);
        assertThat(route.chatModel()).isEqualTo(heavyChatModel);
        assertThat(route.providerCredential()).isEqualTo(providerCredential);
        assertThat(route.resolvedHeavyModel().connectionType()).isEqualTo(AiConnectionType.GITHUB_MODELS_USER_LINKED);
        assertThat(route.runtimeDescriptor()).isNotNull();
        assertThat(route.runtimeDescriptor().providerKey()).isEqualTo("github");
        assertThat(route.runtimeDescriptor().modelKey()).isEqualTo("gpt-4.1");
        assertThat(route.runtimeDescriptor().connectionId()).isEqualTo(connectionId);
    }
}
