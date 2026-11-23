package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PlatformCredentialRegistry {

    private static final String PLATFORM_CREDENTIAL_OWNER = "platform";

    private final Environment environment;
    private final AiProviderRuntimeProperties aiProviderRuntimeProperties;

    public PlatformCredentialRegistry(
            final Environment environment,
            final AiProviderRuntimeProperties aiProviderRuntimeProperties) {
        this.environment = environment;
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
    }

    public boolean hasPlatformCredential(final String providerKey) {
        return findPlatformCredential(providerKey).isPresent();
    }

    public Optional<ProviderCredential> findPlatformCredential(final String providerKey) {
        final String normalizedProviderKey = normalizeProviderKey(providerKey);
        final ProductAiProviderRegistry.ProductAiProvider providerDefinition = ProductAiProviderRegistry.find(normalizedProviderKey)
                .filter(definition -> ProductAiProviderRegistry.isVisible(definition.providerKey(), aiProviderRuntimeProperties.testModeEnabled()))
                .orElse(null);
        if (providerDefinition == null || providerDefinition.platformEnvKey() == null || providerDefinition.platformEnvKey().isBlank()) {
            return Optional.empty();
        }

        final String apiKey = environment.getProperty(providerDefinition.platformEnvKey());
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new ProviderCredential(normalizedProviderKey, apiKey.trim(), CredentialSource.PLATFORM,
                PLATFORM_CREDENTIAL_OWNER, null));
    }

    public Set<String> activeProviderKeys() {
        return ProductAiProviderRegistry.visibleProviders(aiProviderRuntimeProperties.testModeEnabled()).stream()
                .map(ProductAiProviderRegistry.ProductAiProvider::providerKey)
                .filter(this::hasPlatformCredential)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizeProviderKey(final String providerKey) {
        return providerKey == null ? "" : providerKey.trim().toLowerCase(Locale.ROOT);
    }
}
