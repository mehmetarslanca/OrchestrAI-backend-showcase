package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCredentialRegistryTest {

    @Test
    void shouldResolveOfficialPlatformCredential() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("OPENAI_API_KEY", "sk-openai-platform");

        PlatformCredentialRegistry registry = new PlatformCredentialRegistry(environment, runtimeProperties(false));

        assertThat(registry.findPlatformCredential("openai"))
                .isPresent()
                .get()
                .extracting(ProviderCredential::apiKey)
                .isEqualTo("sk-openai-platform");
    }

    @Test
    void shouldHideTestOnlyProviderWhenTestModeIsDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("OLLAMA_API_KEY", "sk-ollama-platform");

        PlatformCredentialRegistry registry = new PlatformCredentialRegistry(environment, runtimeProperties(false));

        assertThat(registry.findPlatformCredential("ollama")).isEmpty();
        assertThat(registry.activeProviderKeys()).doesNotContain("ollama");
    }

    @Test
    void shouldExposeTestOnlyProviderWhenTestModeIsEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("OLLAMA_API_KEY", "sk-ollama-platform")
                .withProperty("OPENAI_COMPATIBLE_API_KEY", "sk-compat-platform");

        PlatformCredentialRegistry registry = new PlatformCredentialRegistry(environment, runtimeProperties(true));

        assertThat(registry.findPlatformCredential("ollama")).isPresent();
        assertThat(registry.findPlatformCredential("openai-compatible")).isPresent();
        assertThat(registry.activeProviderKeys()).contains("ollama", "openai-compatible");
    }

    private AiProviderRuntimeProperties runtimeProperties(final boolean testModeEnabled) {
        return new AiProviderRuntimeProperties(
                new AiProviderRuntimeProperties.TestModeProperties(testModeEnabled),
                Map.of());
    }
}
