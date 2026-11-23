package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRuntimeMetadataResolverTest {

    @Test
    void shouldMarkOfficialProviderAsEmulatedWhenRoutedToLocalGateway() {
        ProviderRuntimeMetadataResolver resolver = new ProviderRuntimeMetadataResolver(runtimeProperties(true, Map.of(
                "openai", new AiProviderRuntimeProperties.ProviderRuntimeProperties("http://localhost:4000"))));

        ProviderRuntimeMetadata metadata = resolver.resolveForProvider("openai");

        assertThat(metadata.testOnly()).isTrue();
        assertThat(metadata.emulated()).isTrue();
        assertThat(metadata.gateway()).isEqualTo("litellm");
        assertThat(metadata.runtimeProvider()).isEqualTo("ollama");
        assertThat(metadata.runtimeTarget()).isEqualTo("gemma4:e4b");
        assertThat(metadata.badges()).containsExactly("TEST", "EMULATED");
    }

    @Test
    void shouldMarkLocalProviderAsLocalWhenRoutedToGateway() {
        ProviderRuntimeMetadataResolver resolver = new ProviderRuntimeMetadataResolver(runtimeProperties(true, Map.of(
                "ollama", new AiProviderRuntimeProperties.ProviderRuntimeProperties("http://localhost:4000"))));

        ProviderRuntimeMetadata metadata = resolver.resolveForModel("ollama", "gemma4-e4b", "gemma4-e4b");

        assertThat(metadata.testOnly()).isTrue();
        assertThat(metadata.emulated()).isFalse();
        assertThat(metadata.runtimeTarget()).isEqualTo("gemma4:e4b");
        assertThat(metadata.badges()).containsExactly("TEST", "LOCAL");
    }

    private AiProviderRuntimeProperties runtimeProperties(
            final boolean testModeEnabled,
            final Map<String, AiProviderRuntimeProperties.ProviderRuntimeProperties> providers) {
        return new AiProviderRuntimeProperties(
                new AiProviderRuntimeProperties.TestModeProperties(testModeEnabled),
                providers);
    }
}
