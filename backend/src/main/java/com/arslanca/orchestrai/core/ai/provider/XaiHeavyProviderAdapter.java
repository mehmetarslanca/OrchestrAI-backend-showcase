package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(5)
public class XaiHeavyProviderAdapter extends AbstractOpenAiCompatibleHeavyProviderAdapter {

    public XaiHeavyProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties,
            @Value("${orchestrai.ai.providers.xai.base-url:https://api.x.ai}") final String baseUrl) {
        super("xai", baseUrl, platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
    }
}
