package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractOpenAiCompatibleHeavyProviderAdapter extends AbstractRestHeavyProviderAdapter {

    private final String providerKey;
    private final String baseUrl;

    protected AbstractOpenAiCompatibleHeavyProviderAdapter(
            final String providerKey,
            final String baseUrl,
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties) {
        super(platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
        this.providerKey = providerKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public String providerKey() {
        return providerKey;
    }

    @Override
    public List<DiscoveredHeavyModel> listAvailableHeavyModels(final ProviderCredential credential) {
        final JsonNode response = getJson(baseUrl + "/v1/models", headers -> headers.setBearerAuth(credential.apiKey()));
        final List<RawProviderModel> rawModels = new ArrayList<>();
        for (JsonNode node : response.path("data")) {
            final String modelId = readText(node, "id");
            if (modelId != null && !modelId.isBlank() && supportsModelId(modelId)) {
                rawModels.add(new RawProviderModel(modelId, displayNameForModel(modelId)));
            }
        }
        return normalizeDiscoveredModels(rawModels, credential);
    }

    @Override
    public ChatModel buildHeavyChatModel(final ResolvedHeavyModel resolvedModel, final ProviderCredential credential) {
        return new OpenAiCompatibleHeavyChatModel(
                heavyAiRestClientBuilder,
                objectMapper,
                baseUrl,
                credential,
                resolvedModel.concreteModelId(),
                aiModelProperties.heavyModelTemperature(),
                aiModelProperties.heavyModelMaxTokens());
    }

    protected boolean supportsModelId(final String modelId) {
        return true;
    }

    protected String displayNameForModel(final String modelId) {
        return modelId;
    }
}
