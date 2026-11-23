package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.ai.HeavyModelCatalogResponse;
import com.arslanca.orchestrai.business.dtos.ai.CatalogModelAvailabilityState;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.PlatformCredentialRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadataResolver;
import com.arslanca.orchestrai.core.config.AiModelProperties;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeavyModelCatalogManagerTest {

  private static final AiModelProperties AI_MODEL_PROPERTIES = new AiModelProperties(
      "gemini-3-flash-preview", 0.2, 2000, "google:gemini-3-pro-preview", 0.3, 8000);

  @Mock
  private PlatformCredentialRegistry platformCredentialRegistry;

  @Mock
  private HeavyProviderAdapter googleAdapter;

  @Mock
  private HeavyProviderAdapter openAiAdapter;

  @Mock
  private ProviderCredentialManager providerCredentialManager;

  @Test
    void shouldReturnSanitizedCatalogForAvailablePlatformModels() {
    ProviderCredential googleCredential = new ProviderCredential(
        "google", "secret", CredentialSource.PLATFORM, "platform");

    when(googleAdapter.providerKey()).thenReturn("google");
    when(googleAdapter.supportsPlatformCredential()).thenReturn(true);
    when(platformCredentialRegistry.findPlatformCredential("google")).thenReturn(Optional.of(googleCredential));
    when(googleAdapter.listAvailableHeavyModels(googleCredential)).thenReturn(List.of(
        new DiscoveredHeavyModel(
            "google",
            "gemini-3-pro",
            "models/gemini-3-pro",
            "Gemini 3 Pro",
            CredentialSource.PLATFORM,
            "platform")));

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(googleAdapter),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager.getCatalog();

    assertThat(response.lightModel().family()).isEqualTo("gemini-3-flash-preview");
    assertThat(response.heavyModels()).hasSize(1);
    assertThat(response.heavyModels().get(0).modelKey()).isEqualTo("google:gemini-3-pro");
    assertThat(response.heavyModels().get(0).source()).isEqualTo("PLATFORM");
    assertThat(response.catalogModels()).isNotEmpty();
    assertThat(response.catalogModels())
        .anySatisfy(model -> {
          assertThat(model.modelKey()).isEqualTo("google:gemini-3-pro");
          assertThat(model.availabilityState()).isEqualTo(CatalogModelAvailabilityState.SELECTABLE);
          assertThat(model.executionClass()).isEqualTo(AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE);
        });
  }

  @Test
  void shouldSkipProviderWhenCatalogLookupFails() {
    ProviderCredential openAiCredential = new ProviderCredential(
        "openai", "secret", CredentialSource.PLATFORM, "platform");

    when(openAiAdapter.providerKey()).thenReturn("openai");
    when(openAiAdapter.supportsPlatformCredential()).thenReturn(true);
    when(platformCredentialRegistry.findPlatformCredential("openai")).thenReturn(Optional.of(openAiCredential));
    when(openAiAdapter.listAvailableHeavyModels(openAiCredential)).thenThrow(new IllegalStateException("boom"));

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(openAiAdapter),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager.getCatalog();

    assertThat(response.heavyModels()).isEmpty();
  }

  @Test
  void shouldReturnEmptyCatalogWhenNoProvidersAreAvailable() {
    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager.getCatalog();

    assertThat(response.heavyModels()).isEmpty();
  }

  @Test
  void shouldReuseCachedCatalogUntilTtlExpires() {
    ProviderCredential googleCredential = new ProviderCredential(
        "google", "secret", CredentialSource.PLATFORM, "platform");
    MutableClock clock = new MutableClock(Instant.parse("2026-03-25T19:00:00Z"));

    when(googleAdapter.providerKey()).thenReturn("google");
    when(googleAdapter.supportsPlatformCredential()).thenReturn(true);
    when(platformCredentialRegistry.findPlatformCredential("google")).thenReturn(Optional.of(googleCredential));
    when(googleAdapter.listAvailableHeavyModels(googleCredential)).thenReturn(List.of(
        new DiscoveredHeavyModel(
            "google",
            "gemini-3-pro",
            "models/gemini-3-pro",
            "Gemini 3 Pro",
            CredentialSource.PLATFORM,
            "platform")));

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(googleAdapter),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        clock);

    manager.getAvailablePlatformModels();
    manager.getAvailablePlatformModels();

    verify(googleAdapter, times(1)).listAvailableHeavyModels(googleCredential);

    clock.advanceSeconds(301);
    manager.getAvailablePlatformModels();

    verify(googleAdapter, times(2)).listAvailableHeavyModels(googleCredential);
  }

  @Test
  void shouldMergeValidByokModelsWithoutTouchingPlatformCache() {
    ProviderCredential googleCredential = new ProviderCredential(
        "google", "secret", CredentialSource.PLATFORM, "platform");

    when(googleAdapter.providerKey()).thenReturn("google");
    when(googleAdapter.supportsPlatformCredential()).thenReturn(true);
    when(platformCredentialRegistry.findPlatformCredential("google")).thenReturn(Optional.of(googleCredential));
    when(googleAdapter.listAvailableHeavyModels(googleCredential)).thenReturn(List.of(
        new DiscoveredHeavyModel(
            "google",
            "gemini-3-pro",
            "models/gemini-3-pro",
            "Gemini 3 Pro",
            CredentialSource.PLATFORM,
            "platform")));
    when(
        providerCredentialManager.getAvailableModels(java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
        .thenReturn(List.of(
            new DiscoveredHeavyModel(
                "anthropic",
                "claude-4.6-sonnet",
                "claude-4.6-sonnet",
                "Claude 4.6 Sonnet",
                CredentialSource.BYOK,
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")));

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(googleAdapter),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager
        .getCatalog(java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    assertThat(response.heavyModels()).hasSize(2);
    assertThat(response.heavyModels()).extracting(model -> model.source())
        .containsExactly("PLATFORM", "BYOK");
    assertThat(response.catalogModels()).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void shouldExposeLockedCatalogModelsWithoutChangingLegacyHeavyModels() {
    UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    when(providerCredentialManager.getAvailableModels(userId)).thenReturn(List.of());
    when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of());

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager.getCatalog(userId);

    assertThat(response.heavyModels()).isEmpty();
    assertThat(response.catalogModels())
        .filteredOn(model -> "OPENAI_BYOK".equals(model.connectionSurface()))
        .isNotEmpty()
        .allSatisfy(model -> {
          assertThat(model.selectable()).isFalse();
          assertThat(model.availabilityState()).isEqualTo(CatalogModelAvailabilityState.LOCKED_CONNECTION_REQUIRED);
        });
  }

  @Test
  void shouldExposeReauthRequiredCatalogStateFromCredentialStatus() {
    UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    UUID connectionId = UUID.randomUUID();
    when(providerCredentialManager.getAvailableModels(userId)).thenReturn(List.of());
    when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of(
        new ProviderCredentialStatusResponse(
            "openai",
            ProviderCredentialStatus.INVALID,
            "sk-o...5678",
            null,
            "OpenAI",
            false,
            false,
            null,
            null,
            null,
            null,
            connectionId,
            AiConnectionType.OPENAI_BYOK,
            AiConnectionStatus.REAUTH_REQUIRED,
            AiConnectionAuthMethod.API_KEY,
            AiConnectionBillingMode.BYOK,
            List.of())));

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager.getCatalog(userId);

    assertThat(response.catalogModels())
        .filteredOn(model -> "OPENAI_BYOK".equals(model.connectionSurface()))
        .isNotEmpty()
        .allSatisfy(model -> {
          assertThat(model.availabilityState()).isEqualTo(CatalogModelAvailabilityState.REAUTH_REQUIRED);
          assertThat(model.selectedConnectionId()).isEqualTo(connectionId);
        });
  }

  @Test
  void shouldHideTestOnlyProvidersWhenTestModeIsDisabled() {
    when(
        providerCredentialManager.getAvailableModels(java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
        .thenReturn(List.of(
            new DiscoveredHeavyModel(
                "ollama",
                "qwen2.5-coder-14b",
                "qwen2.5-coder:14b",
                "Qwen2.5 Coder 14B",
                CredentialSource.BYOK,
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")));

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager
        .getCatalog(java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    assertThat(response.heavyModels()).isEmpty();
  }

  @Test
  void shouldDecorateTestOnlyProvidersWhenTestModeIsEnabled() {
    ProviderCredential ollamaCredential = new ProviderCredential(
        "ollama", "secret", CredentialSource.PLATFORM, "platform");

    when(googleAdapter.providerKey()).thenReturn("ollama");
    when(googleAdapter.supportsPlatformCredential()).thenReturn(true);
    when(platformCredentialRegistry.findPlatformCredential("ollama")).thenReturn(Optional.of(ollamaCredential));
    when(googleAdapter.listAvailableHeavyModels(ollamaCredential)).thenReturn(List.of(
        new DiscoveredHeavyModel(
            "ollama",
            "qwen2.5-coder-14b",
            "qwen2.5-coder:14b",
            "Qwen2.5 Coder 14B",
            CredentialSource.PLATFORM,
            "platform")));

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(googleAdapter),
        AI_MODEL_PROPERTIES,
        runtimeProperties(true,
            Map.of("ollama", new AiProviderRuntimeProperties.ProviderRuntimeProperties("http://localhost:4000"))),
        runtimeResolver(true,
            Map.of("ollama", new AiProviderRuntimeProperties.ProviderRuntimeProperties("http://localhost:4000"))),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager.getCatalog();

    assertThat(response.heavyModels()).hasSize(1);
    assertThat(response.heavyModels().get(0).testOnly()).isTrue();
    assertThat(response.heavyModels().get(0).badges()).contains("TEST", "LOCAL");
  }

  @Test
  void shouldMapGithubDiscoveredModelsToExactConnectionSurface() {
    UUID userId = UUID.randomUUID();
    UUID connectionId = UUID.randomUUID();
    when(providerCredentialManager.getAvailableModels(userId)).thenReturn(List.of(
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
    when(providerCredentialManager.getCredentialStatuses(userId)).thenReturn(List.of());

    HeavyModelCatalogManager manager = new HeavyModelCatalogManager(
        platformCredentialRegistry,
        providerCredentialManager,
        List.of(),
        AI_MODEL_PROPERTIES,
        runtimeProperties(false),
        runtimeResolver(false),
        Clock.systemUTC());

    HeavyModelCatalogResponse response = manager.getCatalog(userId);

    assertThat(response.heavyModels())
        .singleElement()
        .satisfies(model -> {
          assertThat(model.connectionSurface()).isEqualTo("GITHUB_MODELS_USER_LINKED");
          assertThat(model.selectedConnectionId()).isEqualTo(connectionId);
        });
  }

  private AiProviderRuntimeProperties runtimeProperties(final boolean testModeEnabled) {
    return runtimeProperties(testModeEnabled, Map.of());
  }

  private AiProviderRuntimeProperties runtimeProperties(
      final boolean testModeEnabled,
      final Map<String, AiProviderRuntimeProperties.ProviderRuntimeProperties> providers) {
    return new AiProviderRuntimeProperties(
        new AiProviderRuntimeProperties.TestModeProperties(testModeEnabled),
        providers);
  }

  private ProviderRuntimeMetadataResolver runtimeResolver(final boolean testModeEnabled) {
    return runtimeResolver(testModeEnabled, Map.of());
  }

  private ProviderRuntimeMetadataResolver runtimeResolver(
      final boolean testModeEnabled,
      final Map<String, AiProviderRuntimeProperties.ProviderRuntimeProperties> providers) {
    return new ProviderRuntimeMetadataResolver(runtimeProperties(testModeEnabled, providers));
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(final Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void advanceSeconds(final long seconds) {
      instant = instant.plusSeconds(seconds);
    }
  }
}
