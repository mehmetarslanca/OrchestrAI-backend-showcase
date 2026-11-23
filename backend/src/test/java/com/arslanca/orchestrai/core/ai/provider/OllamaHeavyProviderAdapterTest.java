package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OllamaHeavyProviderAdapterTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldListOnlyCuratedOllamaModels() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {"id": "gemma4:e4b"},
                                    {"id": "text-embedding-3-small"}
                                  ]
                                }
                                """)));

        OllamaHeavyProviderAdapter adapter = new OllamaHeavyProviderAdapter(
                mock(PlatformCredentialRegistry.class),
                RestClient.builder(),
                new ObjectMapper(),
                properties(),
                wireMockServer.baseUrl());

        List<DiscoveredHeavyModel> models = adapter.listAvailableHeavyModels(
                new ProviderCredential("ollama", "sk-local", CredentialSource.PLATFORM, "platform"));

        assertThat(models).hasSize(1);
        assertThat(models.get(0).family()).isEqualTo("gemma4-e4b");
    }

    @Test
    void shouldPropagateProviderFailuresForDebugging() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(aResponse().withStatus(503)));

        OllamaHeavyProviderAdapter adapter = new OllamaHeavyProviderAdapter(
                mock(PlatformCredentialRegistry.class),
                RestClient.builder(),
                new ObjectMapper(),
                properties(),
                wireMockServer.baseUrl());

        assertThatThrownBy(() -> adapter.listAvailableHeavyModels(
                new ProviderCredential("ollama", "sk-local", CredentialSource.PLATFORM, "platform")))
                .isInstanceOf(org.springframework.web.client.RestClientResponseException.class);
    }

    private AiModelProperties properties() {
        return new AiModelProperties("gemini-3-flash-preview", 0.2, 2000, "google:gemini-3-pro-preview", 0.3, 8000);
    }
}
