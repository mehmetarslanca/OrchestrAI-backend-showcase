package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(6)
public class OllamaHeavyProviderAdapter extends AbstractOpenAiCompatibleHeavyProviderAdapter {

    public OllamaHeavyProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties,
            @Value("${orchestrai.ai.providers.ollama.base-url:http://localhost:4000}") final String baseUrl) {
        super("ollama", baseUrl, platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
    }

    @Override
    protected boolean supportsModelId(final String modelId) {
        return ApprovedHeavyModelRegistry.normalize(providerKey(), modelId).isPresent();
    }
}
