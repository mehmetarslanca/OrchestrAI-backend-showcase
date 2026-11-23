package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(3)
public class MoonshotHeavyProviderAdapter extends AbstractOpenAiCompatibleHeavyProviderAdapter {

    public MoonshotHeavyProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties,
            @Value("${orchestrai.ai.providers.moonshot.base-url:https://api.moonshot.ai}") final String baseUrl) {
        super("moonshot", baseUrl, platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
    }
}
