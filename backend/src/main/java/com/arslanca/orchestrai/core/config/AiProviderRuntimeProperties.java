package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "orchestrai.ai")
public record AiProviderRuntimeProperties(
        TestModeProperties testMode,
        Map<String, ProviderRuntimeProperties> providers) {

    public AiProviderRuntimeProperties {
        testMode = testMode == null ? new TestModeProperties(false) : testMode;

        final Map<String, ProviderRuntimeProperties> normalizedProviders = new LinkedHashMap<>();
        if (providers != null) {
            providers.forEach((providerKey, providerRuntimeProperties) -> normalizedProviders.put(
                    normalizeProviderKey(providerKey),
                    providerRuntimeProperties == null ? new ProviderRuntimeProperties(null) : providerRuntimeProperties));
        }
        providers = Map.copyOf(normalizedProviders);
    }

    public boolean testModeEnabled() {
        return testMode.enabled();
    }

    public Optional<String> baseUrlFor(final String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(providers.get(normalizeProviderKey(providerKey)))
                .map(ProviderRuntimeProperties::baseUrl)
                .filter(baseUrl -> baseUrl != null && !baseUrl.isBlank());
    }

    public boolean isLocalBaseUrl(final String providerKey) {
        return baseUrlFor(providerKey)
                .map(AiProviderRuntimeProperties::isLocalBaseUrlValue)
                .orElse(false);
    }

    public static boolean isLocalBaseUrlValue(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }

        final URI uri = URI.create(baseUrl);
        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }

        return "localhost".equalsIgnoreCase(host)
                || host.startsWith("127.")
                || "0.0.0.0".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host)
                || "host.docker.internal".equalsIgnoreCase(host)
                || host.endsWith(".local");
    }

    private static String normalizeProviderKey(final String providerKey) {
        return providerKey == null ? "" : providerKey.trim().toLowerCase(Locale.ROOT);
    }

    public record TestModeProperties(boolean enabled) {
    }

    public record ProviderRuntimeProperties(
            @Pattern(regexp = "^https?://.+$", message = "Provider base URL must start with http:// or https://")
            String baseUrl) {

        public ProviderRuntimeProperties {
            baseUrl = sanitizeBaseUrl(baseUrl);
        }

        private static String sanitizeBaseUrl(final String rawBaseUrl) {
            if (rawBaseUrl == null) {
                return null;
            }

            final String trimmed = rawBaseUrl.trim();
            if (trimmed.isEmpty()) {
                return null;
            }

            int endIndex = trimmed.length();
            while (endIndex > 1 && trimmed.charAt(endIndex - 1) == '/') {
                endIndex--;
            }
            return trimmed.substring(0, endIndex);
        }
    }
}
