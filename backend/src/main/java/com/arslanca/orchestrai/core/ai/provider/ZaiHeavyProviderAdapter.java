package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(4)
public class ZaiHeavyProviderAdapter extends AbstractOpenAiCompatibleHeavyProviderAdapter {

    public ZaiHeavyProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties,
            @Value("${orchestrai.ai.providers.zai.base-url:https://api.z.ai/api/paas/v4}") final String baseUrl) {
        super("zai", baseUrl, platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
    }
}
