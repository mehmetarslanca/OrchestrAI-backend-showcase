package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class ProviderRuntimeMetadataResolver {

    private static final String LITELLM_GATEWAY = "litellm";
    private static final String OLLAMA_PROVIDER = "ollama";

    private static final Map<String, String> DEFAULT_RUNTIME_TARGETS_BY_PROVIDER_KEY = Map.of(
            "ollama", "gemma4:e4b",
            "openai-compatible", "gemma4:e4b",
            "openai", "gemma4:e4b",
            "anthropic", "gemma4:e4b",
            "google", "gemma4:e4b");

    private static final Map<String, String> LOCAL_TARGETS_BY_MODEL_KEY = Map.ofEntries(
            Map.entry("ollama:gemma4-e4b", "gemma4:e4b"),
            Map.entry("ollama:qwen2.5-coder-14b", "qwen2.5-coder:14b"),
            Map.entry("ollama:qwen2.5-coder-7b", "qwen2.5-coder:7b"),
            Map.entry("ollama:llama-3.1-8b-instruct", "llama3.1:8b-instruct"),
            Map.entry("ollama:deepseek-r1-distill-qwen-14b", "deepseek-r1-distill-qwen:14b"),
            Map.entry("openai-compatible:gemma4-e4b", "gemma4:e4b"),
            Map.entry("openai-compatible:qwen2.5-coder-14b", "qwen2.5-coder:14b"),
            Map.entry("openai-compatible:qwen2.5-coder-7b", "qwen2.5-coder:7b"),
            Map.entry("openai-compatible:llama-3.1-8b-instruct", "llama3.1:8b-instruct"),
            Map.entry("openai-compatible:deepseek-r1-distill-qwen-14b", "deepseek-r1-distill-qwen:14b"),
            Map.entry("openai:gpt-4o", "gemma4:e4b"),
            Map.entry("openai:gpt-4.1", "gemma4:e4b"),
            Map.entry("openai:o4-mini", "gemma4:e4b"),
            Map.entry("anthropic:claude-sonnet-4-5", "gemma4:e4b"),
            Map.entry("google:gemini-2.5-pro", "gemma4:e4b"));

    private final AiProviderRuntimeProperties aiProviderRuntimeProperties;

    public ProviderRuntimeMetadataResolver(final AiProviderRuntimeProperties aiProviderRuntimeProperties) {
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
    }

    public ProviderRuntimeMetadata resolveForProvider(final String providerKey) {
        final String normalizedProviderKey = normalize(providerKey);
        if (!aiProviderRuntimeProperties.testModeEnabled()) {
            return ProviderRuntimeMetadata.none();
        }

        if (isTestOnlyLocalProvider(normalizedProviderKey)) {
            return new ProviderRuntimeMetadata(
                    true,
                    false,
                    LITELLM_GATEWAY,
                    OLLAMA_PROVIDER,
                    defaultRuntimeTargetForProvider(normalizedProviderKey),
                    null,
                    List.of("TEST", "LOCAL"));
        }

        if (isOfficialProviderRoutedToLocalGateway(normalizedProviderKey)) {
            return new ProviderRuntimeMetadata(
                    true,
                    true,
                    LITELLM_GATEWAY,
                    OLLAMA_PROVIDER,
                    defaultRuntimeTargetForProvider(normalizedProviderKey),
                    null,
                    List.of("TEST", "EMULATED"));
        }

        return ProviderRuntimeMetadata.none();
    }

    public ProviderRuntimeMetadata resolveForCredential(final String providerKey, final String apiKey) {
        final ProviderRuntimeMetadata baseMetadata = resolveForProvider(providerKey);
        if (!baseMetadata.testOnly() && !baseMetadata.emulated()) {
            return new ProviderRuntimeMetadata(
                    false,
                    false,
                    null,
                    null,
                    null,
                    "REAL_PROVIDER_KEY",
                    List.of());
        }

        return new ProviderRuntimeMetadata(
                baseMetadata.testOnly(),
                baseMetadata.emulated(),
                baseMetadata.gateway(),
                baseMetadata.runtimeProvider(),
                baseMetadata.runtimeTarget(),
                isVirtualKey(apiKey) ? "VIRTUAL_TEST_KEY" : "REAL_PROVIDER_KEY",
                baseMetadata.badges());
    }

    public ProviderRuntimeMetadata resolveForModel(
            final String providerKey,
            final String family,
            final String concreteModelId) {
        final String normalizedProviderKey = normalize(providerKey);
        final String normalizedFamily = ApprovedHeavyModelRegistry.normalizeKey(family);
        final String modelKey = normalizedProviderKey + ":" + normalizedFamily;

        if (isTestOnlyLocalProvider(normalizedProviderKey)) {
            return new ProviderRuntimeMetadata(
                    true,
                    false,
                    LITELLM_GATEWAY,
                    OLLAMA_PROVIDER,
                    runtimeTargetFor(modelKey, concreteModelId),
                    null,
                    List.of("TEST", "LOCAL"));
        }

        if (isOfficialProviderRoutedToLocalGateway(normalizedProviderKey) && LOCAL_TARGETS_BY_MODEL_KEY.containsKey(modelKey)) {
            return new ProviderRuntimeMetadata(
                    true,
                    true,
                    LITELLM_GATEWAY,
                    OLLAMA_PROVIDER,
                    runtimeTargetFor(modelKey, concreteModelId),
                    null,
                    List.of("TEST", "EMULATED"));
        }

        return ProviderRuntimeMetadata.none();
    }

    private String defaultRuntimeTargetForProvider(final String providerKey) {
        return DEFAULT_RUNTIME_TARGETS_BY_PROVIDER_KEY.get(providerKey);
    }

    private String runtimeTargetFor(final String modelKey, final String concreteModelId) {
        return Optional.ofNullable(LOCAL_TARGETS_BY_MODEL_KEY.get(modelKey))
                .orElse(concreteModelId);
    }

    private boolean isOfficialProviderRoutedToLocalGateway(final String providerKey) {
        return isOfficialProvider(providerKey) && aiProviderRuntimeProperties.isLocalBaseUrl(providerKey);
    }

    private boolean isTestOnlyLocalProvider(final String providerKey) {
        return ("ollama".equals(providerKey) || "openai-compatible".equals(providerKey))
                && aiProviderRuntimeProperties.isLocalBaseUrl(providerKey);
    }

    private boolean isOfficialProvider(final String providerKey) {
        return "google".equals(providerKey) || "openai".equals(providerKey) || "anthropic".equals(providerKey);
    }

    private boolean isVirtualKey(final String apiKey) {
        return apiKey != null && apiKey.startsWith("sk-");
    }

    private String normalize(final String providerKey) {
        return providerKey == null ? "" : providerKey.trim().toLowerCase(Locale.ROOT);
    }
}
