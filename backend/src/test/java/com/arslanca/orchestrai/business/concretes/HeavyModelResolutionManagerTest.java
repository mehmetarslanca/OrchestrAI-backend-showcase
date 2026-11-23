package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.HeavyModelUserPreferenceService;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelPreference;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyModelCapabilityResolver;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadataResolver;
import com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeavyModelResolutionManagerTest {

    @Mock
    private HeavyModelCatalogManager heavyModelCatalogManager;

    @Mock
    private HeavyModelUserPreferenceService heavyModelUserPreferenceService;

    @Mock
    private com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter googleAdapter;

    @Test
    void shouldResolveExplicitRequestedModelWhenAvailable() {
        when(heavyModelCatalogManager.getAvailableModels(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                model("google", "gemini-3-pro"),
                model("openai", "gpt-5.4")));
        when(heavyModelCatalogManager.getAvailablePlatformModels()).thenReturn(List.of(
                model("google", "gemini-3-pro"),
                model("openai", "gpt-5.4")));

        HeavyModelResolutionManager manager = new HeavyModelResolutionManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                properties(),
                runtimeResolver(),
                new HeavyModelCapabilityResolver(),
                List.of(googleAdapter));

        ResolvedHeavyModel resolved = manager.resolve(UUID.randomUUID(), "openai:gpt-5.4");

        assertThat(resolved.modelKey()).isEqualTo("openai:gpt-5.4");
    }

    @Test
    void shouldFailLoudlyWhenExplicitModelIsUnavailable() {
        when(heavyModelCatalogManager.getAvailableModels(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(model("google", "gemini-3-pro")));
        when(heavyModelCatalogManager.getAvailablePlatformModels()).thenReturn(List.of(model("google", "gemini-3-pro")));

        HeavyModelResolutionManager manager = new HeavyModelResolutionManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                properties(),
                runtimeResolver(),
                new HeavyModelCapabilityResolver(),
                List.of(googleAdapter));

        assertThatThrownBy(() -> manager.resolve(UUID.randomUUID(), "openai:gpt-5.4"))
                .isInstanceOf(HeavyModelResolutionException.class)
                .hasMessageContaining("Istenen heavy model");
    }

    @Test
    void shouldPreferConfiguredPlatformDefaultAndFallbackToNextAvailableModel() {
        UUID userId = UUID.randomUUID();
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.empty());
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                model("openai", "gpt-5.4"),
                model("anthropic", "claude-4.6-sonnet")));
        when(heavyModelCatalogManager.getAvailablePlatformModels()).thenReturn(List.of(
                model("openai", "gpt-5.4"),
                model("anthropic", "claude-4.6-sonnet")));

        HeavyModelResolutionManager manager = new HeavyModelResolutionManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                properties(),
                runtimeResolver(),
                new HeavyModelCapabilityResolver(),
                List.of(googleAdapter));

        ResolvedHeavyModel resolved = manager.resolve(userId, null);

        assertThat(resolved.modelKey()).isEqualTo("openai:gpt-5.4");
    }

    @Test
    void shouldFailWhenSavedByokPreferenceIsNoLongerAvailable() {
        UUID userId = UUID.randomUUID();
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.of(
                new HeavyModelPreference("anthropic:claude-4.6-sonnet", CredentialSource.BYOK)));
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                model("openai", "gpt-5.4")));
        when(heavyModelCatalogManager.getAvailablePlatformModels()).thenReturn(List.of(
                model("openai", "gpt-5.4")));

        HeavyModelResolutionManager manager = new HeavyModelResolutionManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                properties(),
                runtimeResolver(),
                new HeavyModelCapabilityResolver(),
                List.of(googleAdapter));

        assertThatThrownBy(() -> manager.resolve(userId, null, null))
                .isInstanceOf(HeavyModelResolutionException.class)
                .hasMessageContaining("Kayitli default heavy model");
    }

    @Test
    void shouldPreferByokWhenExplicitRequestMatchesTwoSourcesAndNoAuthSourceGiven() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                model("openai", "gpt-5.4"),
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4",
                        "GPT-5.4",
                        CredentialSource.BYOK,
                        userId.toString(),
                        connectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE)));
        when(heavyModelCatalogManager.getAvailablePlatformModels()).thenReturn(List.of(
                model("openai", "gpt-5.4")));
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.empty());

        HeavyModelResolutionManager manager = new HeavyModelResolutionManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                properties(),
                runtimeResolver(),
                new HeavyModelCapabilityResolver(),
                List.of(googleAdapter));

        ResolvedHeavyModel resolved = manager.resolve(userId, "openai:gpt-5.4", null);

        assertThat(resolved.source()).isEqualTo(CredentialSource.BYOK);
        assertThat(resolved.selectedConnectionId()).isEqualTo(connectionId);
        assertThat(resolved.executionClass()).isEqualTo(AiConnectionExecutionClass.USER_SINGLE_ROUTE);
    }

    @Test
    void shouldResolveSavedPreferenceByExactSelectedConnection() {
        UUID userId = UUID.randomUUID();
        UUID selectedConnectionId = UUID.randomUUID();
        UUID otherConnectionId = UUID.randomUUID();
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.of(
                new HeavyModelPreference("openai:gpt-5.4", CredentialSource.BYOK, selectedConnectionId)));
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4-a",
                        "GPT-5.4 A",
                        CredentialSource.BYOK,
                        userId.toString(),
                        otherConnectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE),
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4-b",
                        "GPT-5.4 B",
                        CredentialSource.BYOK,
                        userId.toString(),
                        selectedConnectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE)));
        when(heavyModelCatalogManager.getAvailablePlatformModels()).thenReturn(List.of(model("google", "gemini-3-pro")));

        HeavyModelResolutionManager manager = new HeavyModelResolutionManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                properties(),
                runtimeResolver(),
                new HeavyModelCapabilityResolver(),
                List.of(googleAdapter));

        ResolvedHeavyModel resolved = manager.resolve(userId, null, null);

        assertThat(resolved.selectedConnectionId()).isEqualTo(selectedConnectionId);
        assertThat(resolved.concreteModelId()).isEqualTo("gpt-5.4-b");
    }

    private com.arslanca.orchestrai.core.config.AiModelProperties properties() {
        return new com.arslanca.orchestrai.core.config.AiModelProperties(
                "gemini-1.5-flash", 0.2, 2000, "google:gemini-3-pro", 0.3, 8000);
    }

    private ProviderRuntimeMetadataResolver runtimeResolver() {
        return new ProviderRuntimeMetadataResolver(new com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties(
                new com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties.TestModeProperties(false),
                java.util.Map.of()));
    }

    private DiscoveredHeavyModel model(final String provider, final String family) {
        return new DiscoveredHeavyModel(provider, family, family, family, CredentialSource.PLATFORM, "platform");
    }
}
