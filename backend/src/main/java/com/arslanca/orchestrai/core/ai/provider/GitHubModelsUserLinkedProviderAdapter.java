package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(20)
public class GitHubModelsUserLinkedProviderAdapter extends AbstractGitHubUserLinkedHeavyProviderAdapter {

    public GitHubModelsUserLinkedProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties,
            @Value("${orchestrai.ai.providers.github.base-url:https://models.github.ai}") final String baseUrl) {
        super(baseUrl, platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
    }

    @Override
    protected AiConnectionType supportedConnectionType() {
        return AiConnectionType.GITHUB_MODELS_USER_LINKED;
    }
}
