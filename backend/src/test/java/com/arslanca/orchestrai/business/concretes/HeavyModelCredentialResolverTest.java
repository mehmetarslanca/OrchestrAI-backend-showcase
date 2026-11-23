package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.PlatformCredentialRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeavyModelCredentialResolverTest {

    @Mock
    private PlatformCredentialRegistry platformCredentialRegistry;

    @Mock
    private ProviderCredentialManager providerCredentialManager;

    @Test
    void shouldResolvePlatformCredential() {
        ProviderCredential credential = new ProviderCredential("openai", "platform-key", CredentialSource.PLATFORM, "platform");
        when(platformCredentialRegistry.findPlatformCredential("openai")).thenReturn(Optional.of(credential));

        HeavyModelCredentialResolver resolver = new HeavyModelCredentialResolver(platformCredentialRegistry, providerCredentialManager);

        ProviderCredential resolved = resolver.resolve(new ResolvedHeavyModel(
                "openai:gpt-5.4",
                "openai",
                "gpt-5.4",
                CredentialSource.PLATFORM,
                "platform",
                "gpt-5.4",
                "GPT-5.4"));

        assertThat(resolved).isEqualTo(credential);
    }

    @Test
    void shouldFailWhenByokCredentialIsMissing() {
        UUID userId = UUID.randomUUID();
        when(providerCredentialManager.findValidCredential(userId, "openai")).thenReturn(Optional.empty());

        HeavyModelCredentialResolver resolver = new HeavyModelCredentialResolver(platformCredentialRegistry, providerCredentialManager);

        assertThatThrownBy(() -> resolver.resolve(new ResolvedHeavyModel(
                "openai:gpt-5.4",
                "openai",
                "gpt-5.4",
                CredentialSource.BYOK,
                userId.toString(),
                "gpt-5.4",
                "GPT-5.4")))
                .isInstanceOf(HeavyModelResolutionException.class)
                .hasMessageContaining("BYOK credential");
    }

    @Test
    void shouldResolveByokCredentialBySelectedConnection() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        ProviderCredential credential = new ProviderCredential(
                "openai",
                "byok-key",
                CredentialSource.BYOK,
                userId.toString(),
                connectionId);
        when(providerCredentialManager.findValidCredential(userId, connectionId)).thenReturn(Optional.of(credential));

        HeavyModelCredentialResolver resolver = new HeavyModelCredentialResolver(platformCredentialRegistry, providerCredentialManager);

        ProviderCredential resolved = resolver.resolve(new ResolvedHeavyModel(
                "openai:gpt-5.4",
                "openai",
                "gpt-5.4",
                CredentialSource.BYOK,
                userId.toString(),
                "gpt-5.4",
                "GPT-5.4",
                false,
                false,
                null,
                null,
                null,
                java.util.List.of(),
                com.arslanca.orchestrai.business.dtos.ai.ModelCapability.UNKNOWN,
                connectionId,
                com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass.USER_SINGLE_ROUTE));

        assertThat(resolved).isEqualTo(credential);
    }

    @Test
    void shouldResolveUserLinkedRuntimeCredentialBySelectedConnection() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        ProviderCredential credential = new ProviderCredential(
                "github",
                "oauth-token",
                CredentialSource.USER_LINKED,
                userId.toString(),
                connectionId,
                com.arslanca.orchestrai.entity.enums.AiConnectionType.GITHUB_MODELS_USER_LINKED);
        when(providerCredentialManager.findValidCredential(userId, connectionId)).thenReturn(Optional.of(credential));

        HeavyModelCredentialResolver resolver = new HeavyModelCredentialResolver(platformCredentialRegistry, providerCredentialManager);

        ProviderCredential resolved = resolver.resolve(new ResolvedHeavyModel(
                "github:gpt-4.1",
                "github",
                "gpt-4.1",
                CredentialSource.USER_LINKED,
                userId.toString(),
                "gpt-4.1",
                "GPT-4.1",
                false,
                false,
                null,
                null,
                null,
                java.util.List.of(),
                com.arslanca.orchestrai.business.dtos.ai.ModelCapability.UNKNOWN,
                connectionId,
                com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                com.arslanca.orchestrai.entity.enums.AiConnectionType.GITHUB_MODELS_USER_LINKED));

        assertThat(resolved).isEqualTo(credential);
    }
}
