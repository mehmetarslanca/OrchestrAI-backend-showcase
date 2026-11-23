package com.arslanca.orchestrai.core.ai.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovedHeavyModelRegistryTest {

    @Test
    void shouldNormalizeOllamaFamiliesFromColonModelIds() {
        assertThat(ApprovedHeavyModelRegistry.normalize("ollama", "qwen2.5-coder:14b"))
                .isPresent()
                .get()
                .extracting(ApprovedHeavyModelRegistry.ApprovedFamily::family)
                .isEqualTo("qwen2.5-coder-14b");
    }

    @Test
    void shouldAllowCuratedOpenAiCompatibleFamilies() {
        assertThat(ApprovedHeavyModelRegistry.normalize("openai-compatible", "gemma4:e4b"))
                .isPresent();
    }

    @Test
    void shouldRejectUnknownOpenAiCompatibleFamilies() {
        assertThat(ApprovedHeavyModelRegistry.normalize("openai-compatible", "random-model-123"))
                .isEmpty();
    }
}
