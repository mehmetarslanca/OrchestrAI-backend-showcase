package com.arslanca.orchestrai.core.ai.provider;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;

@Component
public class HeavyModelCapabilityResolver {

    public ModelCapability resolveCapability(
            final String provider,
            final String family,
            final ProviderRuntimeMetadata runtimeMetadata) {
        final String normalizedProvider = normalize(provider);
        final String normalizedFamily = ApprovedHeavyModelRegistry.normalizeKey(family);
        final boolean localRuntime = "ollama".equals(normalizedProvider)
                || "openai-compatible".equals(normalizedProvider)
                || "ollama".equals(normalize(runtimeMetadata == null ? null : runtimeMetadata.runtimeProvider()));

        if (localRuntime) {
            if (normalizedFamily.contains("14b")) {
                return ModelCapability.MODERATE;
            }
            return ModelCapability.LOW;
        }

        if (normalizedFamily.contains("nano")
                || normalizedFamily.contains("mini")
                || normalizedFamily.contains("lite")
                || normalizedFamily.contains("flash")
                || normalizedFamily.contains("haiku")) {
            return ModelCapability.MODERATE;
        }

        if (normalizedFamily.contains("pro")
                || normalizedFamily.contains("opus")
                || normalizedFamily.contains("sonnet")
                || normalizedFamily.contains("gpt-5")
                || normalizedFamily.contains("gpt-4")
                || normalizedFamily.contains("o3")
                || normalizedFamily.contains("glm-5")
                || normalizedFamily.contains("grok")
                || normalizedFamily.contains("kimi")) {
            return ModelCapability.HIGH;
        }

        return ModelCapability.HIGH;
    }

    private String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
