package com.arslanca.orchestrai.core.config;

import com.arslanca.orchestrai.core.ai.provider.ApprovedHeavyModelRegistry;
import com.arslanca.orchestrai.core.ai.provider.PlatformCredentialRegistry;
import com.arslanca.orchestrai.core.qualifier.LightModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * Spring AI configuration for the dual-LLM pipeline.
 *
 * <p>
 * Creates the platform-managed light {@link ChatModel} bean from the
 * Google GenAI provider (Gemini).
 * <ul>
 * <li>{@link LightModel} — fast, cheap model for routing/analysis</li>
 * </ul>
 * </p>
 *
 * <p>
 * Changing models or providers requires only configuration changes —
 * service code never references provider-specific classes.
 * </p>
 */
@Configuration
public class AiConfig {

        private static final Logger LOGGER = LoggerFactory.getLogger(AiConfig.class);

        private final AiModelProperties aiModelProperties;
        private final AiProviderRuntimeProperties aiProviderRuntimeProperties;
        private final PlatformCredentialRegistry platformCredentialRegistry;

    public AiConfig(
            final AiModelProperties aiModelProperties,
            final AiProviderRuntimeProperties aiProviderRuntimeProperties,
            final PlatformCredentialRegistry platformCredentialRegistry) {
        this.aiModelProperties = aiModelProperties;
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
        this.platformCredentialRegistry = platformCredentialRegistry;
    }


        /**
         * Stage 1: Lightweight model for prompt analysis and security filtering.
         *
         * <p>
         * Configured with low temperature and limited max tokens for fast,
         * deterministic structured JSON output.
         * </p>
         */
        @Bean
        @Primary
        @LightModel
        public ChatModel lightChatModel(
                        final ObjectProvider<Client> genAiClientProvider,
                        final ToolCallingManager toolCallingManager,
                        final RetryTemplate retryTemplate,
                        final RestClient.Builder lightAiRestClientBuilder,
                        final ObjectMapper objectMapper) {

                if (shouldUseLocalLightModel()) {
                        final String baseUrl = aiProviderRuntimeProperties.baseUrlFor("openai-compatible")
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "OpenAI-compatible base URL is required for local light model routing"));
                        final String apiKey = platformCredentialRegistry.findPlatformCredential("openai-compatible")
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "OPENAI_COMPATIBLE_API_KEY is required for local light model routing"))
                                        .apiKey();

                        LOGGER.info("Initializing Light ChatModel via local gateway: model={}, baseUrl={}",
                                        aiModelProperties.lightModel(),
                                        baseUrl);

                        return new OpenAiCompatibleLightChatModel(
                                        lightAiRestClientBuilder,
                                        objectMapper,
                                        baseUrl,
                                        apiKey,
                                        aiModelProperties.lightModel(),
                                        aiModelProperties.lightModelTemperature(),
                                        aiModelProperties.lightModelMaxTokens(),
                                        toolCallingManager);
                }

                final Client genAiClient = genAiClientProvider.getIfAvailable();
                if (genAiClient == null) {
                        throw new IllegalStateException("Google GenAI client is not available for the configured light model path.");
                }

                final var options = GoogleGenAiChatOptions.builder()
                                .model(aiModelProperties.lightModel())
                                .temperature(aiModelProperties.lightModelTemperature())
                                .maxOutputTokens(aiModelProperties.lightModelMaxTokens())
                                .build();

                LOGGER.info("Initializing Light ChatModel: model={}, temperature={}, maxTokens={}",
                                aiModelProperties.lightModel(),
                                aiModelProperties.lightModelTemperature(),
                                aiModelProperties.lightModelMaxTokens());

                return GoogleGenAiChatModel.builder()
                                .genAiClient(genAiClient)
                                .defaultOptions(options)
                                .toolCallingManager(toolCallingManager)
                                .retryTemplate(retryTemplate)
                                .build();
        }

        private boolean shouldUseLocalLightModel() {
                return aiProviderRuntimeProperties.testModeEnabled()
                                && aiProviderRuntimeProperties.isLocalBaseUrl("openai-compatible")
                                && platformCredentialRegistry.findPlatformCredential("openai-compatible").isPresent()
                                && ApprovedHeavyModelRegistry.normalize("openai-compatible", aiModelProperties.lightModel()).isPresent();
        }

}
