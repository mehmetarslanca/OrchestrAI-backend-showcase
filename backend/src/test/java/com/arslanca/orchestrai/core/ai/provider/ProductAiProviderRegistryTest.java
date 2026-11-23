package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAiProviderRegistryTest {

    @Test
    void shouldNormalizeProviderKeysWhenResolvingProviderDefinitions() {
        assertThat(ProductAiProviderRegistry.find("  OpenAI  "))
                .isPresent()
                .get()
                .extracting(ProductAiProviderRegistry.ProductAiProvider::providerKey)
                .isEqualTo("openai");

        assertThat(ProductAiProviderRegistry.isKnown("\nAnthropic\t")).isTrue();
    }

    @Test
    void shouldExposeDeferredUserLinkedConnectionSurfacesDeterministically() {
        ProductAiProviderRegistry.ProductAiConnectionSurface githubModelsSurface = ProductAiProviderRegistry
                .findConnectionSurface(AiConnectionType.GITHUB_MODELS_USER_LINKED)
                .orElseThrow();

        assertThat(githubModelsSurface.providerKey()).isEqualTo("github");
        assertThat(githubModelsSurface.authMethod()).isEqualTo(AiConnectionAuthMethod.OAUTH);
        assertThat(githubModelsSurface.billingMode()).isEqualTo(AiConnectionBillingMode.USER_LINKED);
        assertThat(githubModelsSurface.deferred()).isFalse();
        assertThat(githubModelsSurface.supported()).isTrue();
        assertThat(githubModelsSurface.surfaceKey()).isEqualTo("GITHUB_MODELS_USER_LINKED");

        assertThat(ProductAiProviderRegistry.findConnectionSurface(" GitHub ", CredentialSource.USER_LINKED))
                .isPresent()
                .get()
                .extracting(ProductAiProviderRegistry.ProductAiConnectionSurface::connectionType)
                .isEqualTo(AiConnectionType.GITHUB_MODELS_USER_LINKED);
    }

    @Test
    void shouldFilterTestOnlySurfacesWithoutHidingDeferredOrPlatformSurfaces() {
        List<AiConnectionType> productionSurfaceTypes = ProductAiProviderRegistry.visibleConnectionSurfaces(false).stream()
                .map(ProductAiProviderRegistry.ProductAiConnectionSurface::connectionType)
                .toList();

        assertThat(productionSurfaceTypes)
                .doesNotContain(AiConnectionType.OLLAMA_BYOK, AiConnectionType.OPENAI_COMPATIBLE_BYOK)
                .contains(AiConnectionType.OPENAI_APP_PAID, AiConnectionType.GITHUB_MODELS_USER_LINKED, AiConnectionType.OPENAI_CODEX_CHATGPT);

        List<AiConnectionType> testSurfaceTypes = ProductAiProviderRegistry.visibleConnectionSurfaces(true).stream()
                .map(ProductAiProviderRegistry.ProductAiConnectionSurface::connectionType)
                .toList();

        assertThat(testSurfaceTypes)
                .contains(AiConnectionType.OLLAMA_BYOK, AiConnectionType.OPENAI_COMPATIBLE_BYOK);
    }

    @Test
    void shouldResolveGithubSurfaceByExplicitConnectionTypeWithoutAmbiguity() {
        assertThat(ProductAiProviderRegistry.findConnectionSurface("github", CredentialSource.USER_LINKED, AiConnectionType.GITHUB_COPILOT_USER_LINKED))
                .isPresent()
                .get()
                .extracting(ProductAiProviderRegistry.ProductAiConnectionSurface::displayName)
                .isEqualTo("GitHub Copilot");
    }
}
