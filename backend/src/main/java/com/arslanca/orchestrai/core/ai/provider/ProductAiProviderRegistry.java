package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProductAiProviderRegistry {

    private static final Map<String, ProductAiProvider> PROVIDERS_BY_KEY = List.of(
                    provider("google", "Google Gemini", "GEMINI_API_KEY", 0, false, false, List.of()),
                    provider("openai", "OpenAI", "OPENAI_API_KEY", 1, false, false, List.of()),
                    provider("github", "GitHub AI", null, 2, false, false, List.of()),
                    provider("anthropic", "Anthropic", "ANTHROPIC_API_KEY", 3, false, false, List.of()),
                    provider("ollama", "Ollama", "OLLAMA_API_KEY", 4, true, false, List.of("TEST", "LOCAL")),
                    provider("openai-compatible", "OpenAI Compatible", "OPENAI_COMPATIBLE_API_KEY", 5, true, false,
                            List.of("TEST", "LOCAL")))
            .stream()
            .collect(Collectors.toUnmodifiableMap(ProductAiProvider::providerKey, Function.identity()));
    private static final Map<AiConnectionType, ProductAiConnectionSurface> CONNECTION_SURFACES_BY_TYPE = List.of(
                    connectionSurface(AiConnectionType.OPENAI_BYOK, "openai", "OpenAI API Key", "openai", CredentialSource.BYOK,
                            AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK, 0, true, false, false, false, List.of()),
                    connectionSurface(AiConnectionType.GOOGLE_BYOK, "google", "Google Gemini API Key", "google", CredentialSource.BYOK,
                            AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK, 1, true, false, false, false, List.of()),
                    connectionSurface(AiConnectionType.ANTHROPIC_BYOK, "anthropic", "Anthropic API Key", "anthropic", CredentialSource.BYOK,
                            AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK, 2, true, false, false, false, List.of()),
                    connectionSurface(AiConnectionType.OLLAMA_BYOK, "ollama", "Ollama API Key", "ollama", CredentialSource.BYOK,
                            AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK, 3, true, false, true, false, List.of("TEST", "LOCAL")),
                    connectionSurface(AiConnectionType.OPENAI_COMPATIBLE_BYOK, "openai-compatible", "OpenAI Compatible API Key",
                            "openai-compatible", CredentialSource.BYOK, AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK,
                            4, true, false, true, false, List.of("TEST", "LOCAL")),
                    connectionSurface(AiConnectionType.MOONSHOT_BYOK, "moonshot", "Moonshot API Key", "moonshot", CredentialSource.BYOK,
                            AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK, 5, true, false, false, false, List.of()),
                    connectionSurface(AiConnectionType.ZAI_BYOK, "zai", "ZAI API Key", "zai", CredentialSource.BYOK,
                            AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK, 6, true, false, false, false, List.of()),
                    connectionSurface(AiConnectionType.XAI_BYOK, "xai", "xAI API Key", "xai", CredentialSource.BYOK,
                            AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK, 7, true, false, false, false, List.of()),
                    connectionSurface(AiConnectionType.OPENAI_APP_PAID, "openai", "OpenAI App-Paid", null, CredentialSource.PLATFORM,
                            AiConnectionAuthMethod.PLATFORM, AiConnectionBillingMode.PLATFORM_PAID, 8, true, false, false, false,
                            List.of("PLATFORM")),
                    connectionSurface(AiConnectionType.GITHUB_MODELS_USER_LINKED, "github", "GitHub Models", null, CredentialSource.USER_LINKED,
                            AiConnectionAuthMethod.OAUTH, AiConnectionBillingMode.USER_LINKED, 9, true, false, false, false,
                            List.of("USER_LINKED", "OAUTH")),
                    connectionSurface(AiConnectionType.GITHUB_COPILOT_USER_LINKED, "github", "GitHub Copilot", null, CredentialSource.USER_LINKED,
                            AiConnectionAuthMethod.OAUTH, AiConnectionBillingMode.USER_LINKED, 10, true, false, false, false,
                            List.of("USER_LINKED", "OAUTH")),
                    connectionSurface(AiConnectionType.OPENAI_CODEX_CHATGPT, "openai", "OpenAI Codex / ChatGPT", null, CredentialSource.USER_LINKED,
                            AiConnectionAuthMethod.OAUTH, AiConnectionBillingMode.USER_LINKED, 11, false, true, false, false,
                            List.of("DEFERRED")))
            .stream()
            .collect(Collectors.toUnmodifiableMap(ProductAiConnectionSurface::connectionType, Function.identity()));

    private ProductAiProviderRegistry() {
    }

    public static Optional<ProductAiProvider> find(final String rawProviderKey) {
        return Optional.ofNullable(PROVIDERS_BY_KEY.get(normalizeProviderKey(rawProviderKey)));
    }

    public static boolean isKnown(final String rawProviderKey) {
        return find(rawProviderKey).isPresent();
    }

    public static boolean isVisible(final String rawProviderKey, final boolean testModeEnabled) {
        return find(rawProviderKey)
                .filter(provider -> !provider.testOnly() || testModeEnabled)
                .isPresent();
    }

    public static List<ProductAiProvider> visibleProviders(final boolean testModeEnabled) {
        return PROVIDERS_BY_KEY.values().stream()
                .filter(provider -> !provider.testOnly() || testModeEnabled)
                .sorted(Comparator.comparingInt(ProductAiProvider::displayOrder))
                .toList();
    }

    public static Optional<ProductAiConnectionSurface> findConnectionSurface(final AiConnectionType connectionType) {
        return Optional.ofNullable(connectionType == null ? null : CONNECTION_SURFACES_BY_TYPE.get(connectionType));
    }

    public static Optional<ProductAiConnectionSurface> findConnectionSurface(
            final String providerKey,
            final CredentialSource credentialSource) {
        return visibleConnectionSurfaces(true).stream()
                .filter(surface -> surface.providerKey().equals(normalizeProviderKey(providerKey)))
                .filter(surface -> surface.credentialSource() == credentialSource)
                .findFirst();
    }

    public static Optional<ProductAiConnectionSurface> findConnectionSurface(
            final String providerKey,
            final CredentialSource credentialSource,
            final AiConnectionType connectionType) {
        if (connectionType != null) {
            return findConnectionSurface(connectionType)
                    .filter(surface -> surface.providerKey().equals(normalizeProviderKey(providerKey)))
                    .filter(surface -> surface.credentialSource() == credentialSource);
        }
        return findConnectionSurface(providerKey, credentialSource);
    }

    public static List<ProductAiConnectionSurface> visibleConnectionSurfaces(final boolean testModeEnabled) {
        return CONNECTION_SURFACES_BY_TYPE.values().stream()
                .filter(surface -> !surface.testOnly() || testModeEnabled)
                .sorted(Comparator.comparingInt(ProductAiConnectionSurface::displayOrder))
                .toList();
    }

    private static ProductAiProvider provider(
            final String providerKey,
            final String displayName,
            final String platformEnvKey,
            final int displayOrder,
            final boolean testOnly,
            final boolean emulated,
            final List<String> badges) {
        return new ProductAiProvider(providerKey, displayName, platformEnvKey, displayOrder, testOnly, emulated, badges);
    }

    private static String normalizeProviderKey(final String rawProviderKey) {
        return rawProviderKey == null ? "" : rawProviderKey.trim().toLowerCase(Locale.ROOT);
    }

    private static ProductAiConnectionSurface connectionSurface(
            final AiConnectionType connectionType,
            final String providerKey,
            final String displayName,
            final String legacyProviderKey,
            final CredentialSource credentialSource,
            final AiConnectionAuthMethod authMethod,
            final AiConnectionBillingMode billingMode,
            final int displayOrder,
            final boolean supported,
            final boolean deferred,
            final boolean testOnly,
            final boolean emulated,
            final List<String> badges) {
        return new ProductAiConnectionSurface(
                connectionType,
                providerKey,
                displayName,
                legacyProviderKey,
                credentialSource,
                authMethod,
                billingMode,
                displayOrder,
                supported,
                deferred,
                testOnly,
                emulated,
                badges);
    }

    public record ProductAiProvider(
            String providerKey,
            String displayName,
            String platformEnvKey,
            int displayOrder,
            boolean testOnly,
            boolean emulated,
            List<String> badges) {

        public ProductAiProvider {
            badges = badges == null ? List.of() : List.copyOf(badges);
        }
    }

    public record ProductAiConnectionSurface(
            AiConnectionType connectionType,
            String providerKey,
            String displayName,
            String legacyProviderKey,
            CredentialSource credentialSource,
            AiConnectionAuthMethod authMethod,
            AiConnectionBillingMode billingMode,
            int displayOrder,
            boolean supported,
            boolean deferred,
            boolean testOnly,
            boolean emulated,
            List<String> badges) {

        public ProductAiConnectionSurface {
            providerKey = normalizeProviderKey(providerKey);
            badges = badges == null ? List.of() : List.copyOf(badges);
        }

        public String surfaceKey() {
            return connectionType.name();
        }
    }
}
