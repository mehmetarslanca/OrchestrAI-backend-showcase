package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
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

class GoogleHeavyProviderAdapterTest {

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
    void shouldFallbackToLiteLlmModelListWhenGeminiModelsEndpointFails() {
        wireMockServer.stubFor(get(urlEqualTo("/gemini/v1beta/models?key=sk-google"))
                .willReturn(aResponse().withStatus(500)));
        wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {"id": "gemini-2.5-pro"},
                                    {"id": "gpt-4o"}
                                  ]
                                }
                                """)));

        GoogleHeavyProviderAdapter adapter = new GoogleHeavyProviderAdapter(
                mock(PlatformCredentialRegistry.class),
                RestClient.builder(),
                new ObjectMapper(),
                properties(),
                wireMockServer.baseUrl() + "/gemini");

        List<DiscoveredHeavyModel> models = adapter.listAvailableHeavyModels(
                new ProviderCredential("google", "sk-google", CredentialSource.PLATFORM, "platform"));

        assertThat(models).hasSize(1);
        assertThat(models.get(0).family()).isEqualTo("gemini-2.5-pro");
    }

    @Test
    void shouldUseOpenAiCompatibleChatCompletionsWhenGeminiRoutesToLocalGateway() {
        GoogleHeavyProviderAdapter adapter = new GoogleHeavyProviderAdapter(
                mock(PlatformCredentialRegistry.class),
                RestClient.builder(),
                new ObjectMapper(),
                properties(),
                wireMockServer.baseUrl() + "/gemini");

        var chatModel = adapter.buildHeavyChatModel(
                new ResolvedHeavyModel(
                        "google:gemini-2.5-pro",
                        "google",
                        "gemini-2.5-pro",
                        CredentialSource.PLATFORM,
                        "platform",
                        "gemini-2.5-pro",
                        "Gemini 2.5 Pro"),
                new ProviderCredential("google", "sk-google", CredentialSource.PLATFORM, "platform"));

        assertThat(chatModel).isInstanceOf(OpenAiCompatibleHeavyChatModel.class);
    }

    private AiModelProperties properties() {
        return new AiModelProperties("gemini-3-flash-preview", 0.2, 2000, "google:gemini-3-pro-preview", 0.3, 8000);
    }
}
