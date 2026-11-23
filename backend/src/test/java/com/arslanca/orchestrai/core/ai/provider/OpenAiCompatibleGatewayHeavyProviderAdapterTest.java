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
import static org.mockito.Mockito.mock;

class OpenAiCompatibleGatewayHeavyProviderAdapterTest {

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
    void shouldListOnlyCuratedCompatibleModels() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {"id": "llama-3.1-8b-instruct"},
                                    {"id": "gpt-4o"}
                                  ]
                                }
                                """)));

        OpenAiCompatibleGatewayHeavyProviderAdapter adapter = new OpenAiCompatibleGatewayHeavyProviderAdapter(
                mock(PlatformCredentialRegistry.class),
                RestClient.builder(),
                new ObjectMapper(),
                properties(),
                wireMockServer.baseUrl());

        List<DiscoveredHeavyModel> models = adapter.listAvailableHeavyModels(
                new ProviderCredential("openai-compatible", "sk-compat", CredentialSource.PLATFORM, "platform"));

        assertThat(models).hasSize(1);
        assertThat(models.get(0).family()).isEqualTo("llama-3.1-8b-instruct");
    }

    private AiModelProperties properties() {
        return new AiModelProperties("gemini-3-flash-preview", 0.2, 2000, "google:gemini-3-pro-preview", 0.3, 8000);
    }
}
