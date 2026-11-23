package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiUsageLedgerService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelUserPreferenceService;
import com.arslanca.orchestrai.business.dtos.ai.AiConnectionState;
import com.arslanca.orchestrai.business.dtos.ai.AvailableProviderDescriptor;
import com.arslanca.orchestrai.business.dtos.ai.AiSettingsResponse;
import com.arslanca.orchestrai.business.dtos.ai.AiSettingsUpdateRequest;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelPreference;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.business.usage.AiUsageSpendSummary;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadataResolver;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSettingsManagerTest {

    @Mock
    private HeavyModelCatalogManager heavyModelCatalogManager;

    @Mock
    private HeavyModelUserPreferenceService heavyModelUserPreferenceService;

    @Mock
    private ProviderCredentialManager providerCredentialManager;

    @Mock
    private AiUsageLedgerService aiUsageLedgerService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(aiUsageLedgerService.summarizeSpend(any(), any(), any()))
                .thenReturn(spendSummary(null));
        lenient().when(aiUsageLedgerService.summarizeConnectionSpend(any(), any(), any(), any()))
                .thenReturn(spendSummary(null));
    }

    @Test
    void shouldReturnSavedPreferenceAndCredentialStatuses() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.of(
                new HeavyModelPreference("openai:gpt-5.4", CredentialSource.BYOK, connectionId)));
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4",
                        "GPT-5.4",
                        CredentialSource.BYOK,
                        userId.toString(),
                        connectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE)));
        when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of(
                new ProviderCredentialStatusResponse(
                        "openai",
                        ProviderCredentialStatus.VALID,
                        "sk-o...5678",
                        OffsetDateTime.now(),
                        "OpenAI",
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        connectionId,
                        AiConnectionType.OPENAI_BYOK,
                        AiConnectionStatus.VALID,
                        AiConnectionAuthMethod.API_KEY,
                        AiConnectionBillingMode.BYOK,
                        List.of())));

        AiSettingsManager manager = new AiSettingsManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                providerCredentialManager,
                runtimeProperties(false),
                runtimeResolver(false),
                aiUsageLedgerService);

        AiSettingsResponse response = manager.getSettings(userId);

        assertThat(response.defaultHeavyModelKey()).isEqualTo("openai:gpt-5.4");
        assertThat(response.defaultAuthSource()).isEqualTo(CredentialSource.BYOK);
        assertThat(response.testModeEnabled()).isFalse();
        assertThat(response.availableProviders()).extracting(AvailableProviderDescriptor::provider)
                .containsExactly("google", "openai", "github", "anthropic");
        assertThat(response.providerCredentials()).hasSize(1);
        assertThat(response.selectedModel()).isNotNull();
        assertThat(response.selectedModel().selectedConnectionId()).isEqualTo(connectionId);
        assertThat(response.activeModelKey()).isEqualTo("openai:gpt-5.4");
        assertThat(response.activeRoute()).isEqualTo(AiConnectionExecutionClass.USER_SINGLE_ROUTE);
        assertThat(response.aiConnections()).anySatisfy(connection -> {
            assertThat(connection.connectionSurface()).isEqualTo("OPENAI_BYOK");
            assertThat(connection.state()).isEqualTo(AiConnectionState.CONNECTED);
            assertThat(connection.selected()).isTrue();
            assertThat(connection.last30DayEstimatedSpendUsd()).isEqualByComparingTo("0.00000000");
        });
    }

    @Test
    void shouldExposeTestOnlyProvidersWhenTestModeEnabled() {
        UUID userId = UUID.randomUUID();
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.empty());
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of());
        when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of());

        AiSettingsManager manager = new AiSettingsManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                providerCredentialManager,
                runtimeProperties(true),
                runtimeResolver(true),
                aiUsageLedgerService);

        AiSettingsResponse response = manager.getSettings(userId);

        assertThat(response.testModeEnabled()).isTrue();
        assertThat(response.availableProviders()).extracting(AvailableProviderDescriptor::provider)
                .containsExactly("google", "openai", "github", "anthropic", "ollama", "openai-compatible");
    }

    @Test
    void shouldRejectUnavailableModelSelection() {
        UUID userId = UUID.randomUUID();
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                new DiscoveredHeavyModel("google", "gemini-3-pro", "gemini-3-pro", "Gemini 3 Pro", CredentialSource.PLATFORM, "platform")));

        AiSettingsManager manager = new AiSettingsManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                providerCredentialManager,
                runtimeProperties(false),
                runtimeResolver(false),
                aiUsageLedgerService);

        assertThatThrownBy(() -> manager.updateSettings(
                userId,
                new AiSettingsUpdateRequest("openai:gpt-5.4", CredentialSource.BYOK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Secilen heavy model");
    }

    @Test
    void shouldClearPreferenceWhenRequestIsEmpty() {
        UUID userId = UUID.randomUUID();
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.empty());
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of());
        when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of());
        doNothing().when(heavyModelUserPreferenceService).clearPreference(userId);

        AiSettingsManager manager = new AiSettingsManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                providerCredentialManager,
                runtimeProperties(false),
                runtimeResolver(false),
                aiUsageLedgerService);

        AiSettingsResponse response = manager.updateSettings(userId, new AiSettingsUpdateRequest(null, null));

        verify(heavyModelUserPreferenceService).clearPreference(userId);
        assertThat(response.defaultHeavyModelKey()).isNull();
    }

    @Test
    void shouldPersistSelectedConnectionIdentityForByokSelection() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4",
                        "GPT-5.4",
                        CredentialSource.BYOK,
                        userId.toString(),
                        connectionId,
                        com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass.USER_SINGLE_ROUTE)));
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.of(
                new HeavyModelPreference("openai:gpt-5.4", CredentialSource.BYOK, connectionId)));
        when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of());

        AiSettingsManager manager = new AiSettingsManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                providerCredentialManager,
                runtimeProperties(false),
                runtimeResolver(false),
                aiUsageLedgerService);

        AiSettingsResponse response = manager.updateSettings(
                userId,
                new AiSettingsUpdateRequest("openai:gpt-5.4", CredentialSource.BYOK));

        verify(heavyModelUserPreferenceService)
                .savePreference(userId, "openai:gpt-5.4", CredentialSource.BYOK, connectionId);
        assertThat(response.defaultHeavyModelKey()).isEqualTo("openai:gpt-5.4");
        assertThat(response.defaultAuthSource()).isEqualTo(CredentialSource.BYOK);
        assertThat(response.selectedModel()).isNotNull();
        assertThat(response.selectedModel().selectedConnectionId()).isEqualTo(connectionId);
    }

    @Test
    void shouldAcceptGenericSelectedModelFieldsWithoutBreakingLegacyResponse() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4",
                        "GPT-5.4",
                        CredentialSource.BYOK,
                        userId.toString(),
                        connectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE)));
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.of(
                new HeavyModelPreference("openai:gpt-5.4", CredentialSource.BYOK, connectionId)));
        when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of());

        AiSettingsManager manager = new AiSettingsManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                providerCredentialManager,
                runtimeProperties(false),
                runtimeResolver(false),
                aiUsageLedgerService);

        AiSettingsResponse response = manager.updateSettings(
                userId,
                new AiSettingsUpdateRequest("legacy-ignored", CredentialSource.BYOK, "openai:gpt-5.4", connectionId));

        verify(heavyModelUserPreferenceService)
                .savePreference(userId, "openai:gpt-5.4", CredentialSource.BYOK, connectionId);
        assertThat(response.defaultHeavyModelKey()).isEqualTo("openai:gpt-5.4");
        assertThat(response.selectedModel()).isNotNull();
        assertThat(response.selectedModel().executionClass()).isEqualTo(AiConnectionExecutionClass.USER_SINGLE_ROUTE);
    }

    @Test
    void shouldDeriveAuthSourceFromSelectedConnectionWhenGenericRequestOmitsLegacyField() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "openai",
                        "gpt-5.4",
                        "gpt-5.4",
                        "GPT-5.4",
                        CredentialSource.BYOK,
                        userId.toString(),
                        connectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE)));
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.of(
                new HeavyModelPreference("openai:gpt-5.4", CredentialSource.BYOK, connectionId)));
        when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of());

        AiSettingsManager manager = new AiSettingsManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                providerCredentialManager,
                runtimeProperties(false),
                runtimeResolver(false),
                aiUsageLedgerService);

        AiSettingsResponse response = manager.updateSettings(
                userId,
                new AiSettingsUpdateRequest(null, null, "openai:gpt-5.4", connectionId));

        verify(heavyModelUserPreferenceService)
                .savePreference(userId, "openai:gpt-5.4", CredentialSource.BYOK, connectionId);
        assertThat(response.defaultAuthSource()).isEqualTo(CredentialSource.BYOK);
        assertThat(response.selectedModel()).isNotNull();
        assertThat(response.selectedModel().selectedConnectionId()).isEqualTo(connectionId);
    }

    @Test
    void shouldMapGithubUserLinkedConnectionsByExactSurfaceIdentity() {
        UUID userId = UUID.randomUUID();
        UUID modelsConnectionId = UUID.randomUUID();
        UUID copilotConnectionId = UUID.randomUUID();
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.of(
                new HeavyModelPreference("github:gpt-4.1", CredentialSource.USER_LINKED, modelsConnectionId)));
        when(heavyModelCatalogManager.getAvailableModels(userId)).thenReturn(List.of(
                new DiscoveredHeavyModel(
                        "github",
                        "gpt-4.1",
                        "openai/gpt-4.1",
                        "GPT-4.1",
                        CredentialSource.USER_LINKED,
                        userId.toString(),
                        modelsConnectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                        AiConnectionType.GITHUB_MODELS_USER_LINKED),
                new DiscoveredHeavyModel(
                        "github",
                        "gpt-4.1",
                        "openai/gpt-4.1",
                        "GPT-4.1",
                        CredentialSource.USER_LINKED,
                        userId.toString(),
                        copilotConnectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                        AiConnectionType.GITHUB_COPILOT_USER_LINKED)));
        when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of(
                new ProviderCredentialStatusResponse(
                        "github", ProviderCredentialStatus.VALID, null, OffsetDateTime.now(), "GitHub Models", false, false,
                        null, null, "github-models", "OAUTH_USER_LINKED", modelsConnectionId,
                        AiConnectionType.GITHUB_MODELS_USER_LINKED, AiConnectionStatus.VALID,
                        AiConnectionAuthMethod.OAUTH, AiConnectionBillingMode.USER_LINKED, List.of()),
                new ProviderCredentialStatusResponse(
                        "github", ProviderCredentialStatus.VALID, null, OffsetDateTime.now(), "GitHub Copilot", false, false,
                        null, null, "github-copilot", "OAUTH_USER_LINKED", copilotConnectionId,
                        AiConnectionType.GITHUB_COPILOT_USER_LINKED, AiConnectionStatus.VALID,
                        AiConnectionAuthMethod.OAUTH, AiConnectionBillingMode.USER_LINKED, List.of())));

        AiSettingsManager manager = new AiSettingsManager(
                heavyModelCatalogManager,
                heavyModelUserPreferenceService,
                providerCredentialManager,
                runtimeProperties(false),
                runtimeResolver(false),
                aiUsageLedgerService);

        AiSettingsResponse response = manager.getSettings(userId);

        assertThat(response.aiConnections())
                .filteredOn(connection -> connection.connectionSurface().equals("GITHUB_MODELS_USER_LINKED"))
                .singleElement()
                .satisfies(connection -> {
                    assertThat(connection.connected()).isTrue();
                    assertThat(connection.selected()).isTrue();
                });
        assertThat(response.aiConnections())
                .filteredOn(connection -> connection.connectionSurface().equals("GITHUB_COPILOT_USER_LINKED"))
                .singleElement()
                .satisfies(connection -> assertThat(connection.selected()).isFalse());
    }

    private AiProviderRuntimeProperties runtimeProperties(final boolean testModeEnabled) {
        return new AiProviderRuntimeProperties(
                new AiProviderRuntimeProperties.TestModeProperties(testModeEnabled),
                Map.of());
    }

    private ProviderRuntimeMetadataResolver runtimeResolver(final boolean testModeEnabled) {
        return new ProviderRuntimeMetadataResolver(runtimeProperties(testModeEnabled));
    }

    private AiUsageSpendSummary spendSummary(final UUID connectionId) {
        return new AiUsageSpendSummary(connectionId, OffsetDateTime.now().minusDays(30), OffsetDateTime.now(), null, null, 0, null);
    }
}
