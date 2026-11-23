package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(1)
public class OpenAiHeavyProviderAdapter extends AbstractOpenAiCompatibleHeavyProviderAdapter {

    public OpenAiHeavyProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties,
            @Value("${orchestrai.ai.providers.openai.base-url:https://api.openai.com}") final String baseUrl) {
        super("openai", baseUrl, platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
    }

    @Override
    protected boolean supportsModelId(final String modelId) {
        final String normalizedModelId = ApprovedHeavyModelRegistry.normalizeKey(modelId);
        if (normalizedModelId.startsWith("gpt-image")
                || normalizedModelId.startsWith("gpt-realtime")
                || normalizedModelId.contains("audio")
                || normalizedModelId.contains("transcribe")
                || normalizedModelId.endsWith("-tts")
                || normalizedModelId.contains("embedding")
                || normalizedModelId.contains("moderation")
                || normalizedModelId.startsWith("whisper")
                || normalizedModelId.startsWith("dall-e")
                || normalizedModelId.startsWith("sora")) {
            return false;
        }

        return normalizedModelId.startsWith("gpt-")
                || normalizedModelId.startsWith("o1")
                || normalizedModelId.startsWith("o3")
                || normalizedModelId.startsWith("o4");
    }
}
