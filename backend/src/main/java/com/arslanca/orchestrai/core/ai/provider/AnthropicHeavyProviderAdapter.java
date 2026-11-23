package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(2)
public class AnthropicHeavyProviderAdapter extends AbstractRestHeavyProviderAdapter {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String baseUrl;

    public AnthropicHeavyProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties,
            @Value("${orchestrai.ai.providers.anthropic.base-url:https://api.anthropic.com}") final String baseUrl) {
        super(platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
        this.baseUrl = baseUrl;
    }

    @Override
    public String providerKey() {
        return "anthropic";
    }

    @Override
    public List<DiscoveredHeavyModel> listAvailableHeavyModels(final ProviderCredential credential) {
        try {
            final JsonNode response = getJson(baseUrl + "/v1/models", headers -> {
                headers.add("x-api-key", credential.apiKey());
                headers.add("anthropic-version", ANTHROPIC_VERSION);
            });
            final List<RawProviderModel> rawModels = new ArrayList<>();
            for (JsonNode node : response.path("data")) {
                final String modelId = readText(node, "id");
                final String displayName = readText(node, "display_name");
                if (modelId != null && !modelId.isBlank() && ApprovedHeavyModelRegistry.normalizeKey(modelId).startsWith("claude-")) {
                    rawModels.add(new RawProviderModel(modelId, displayName));
                }
            }
            if (!rawModels.isEmpty() || !isLocalPassThroughBaseUrl()) {
                return normalizeDiscoveredModels(rawModels, credential);
            }
        } catch (final RestClientResponseException ex) {
            if (!isLocalPassThroughBaseUrl()) {
                throw ex;
            }
        }

        return listAvailableModelsFromLiteLlmRoot(credential);
    }

    @Override
    public ChatModel buildHeavyChatModel(final ResolvedHeavyModel resolvedModel, final ProviderCredential credential) {
        if (isLocalPassThroughBaseUrl()) {
            return new OpenAiCompatibleHeavyChatModel(
                    heavyAiRestClientBuilder,
                    objectMapper,
                    rootGatewayBaseUrl(),
                    credential,
                    resolvedModel.concreteModelId(),
                    aiModelProperties.heavyModelTemperature(),
                    aiModelProperties.heavyModelMaxTokens());
        }

        return new AnthropicHeavyChatModel(
                heavyAiRestClientBuilder,
                baseUrl,
                credential,
                resolvedModel.concreteModelId(),
                aiModelProperties.heavyModelTemperature(),
                aiModelProperties.heavyModelMaxTokens());
    }

    private List<DiscoveredHeavyModel> listAvailableModelsFromLiteLlmRoot(final ProviderCredential credential) {
        final JsonNode response = getJson(rootGatewayBaseUrl() + "/v1/models", headers -> headers.setBearerAuth(credential.apiKey()));
        final List<RawProviderModel> rawModels = new ArrayList<>();
        for (JsonNode node : response.path("data")) {
            final String modelId = readText(node, "id");
            if (ApprovedHeavyModelRegistry.normalize(providerKey(), modelId).isPresent()) {
                rawModels.add(new RawProviderModel(modelId, readText(node, "id")));
            }
        }
        return normalizeDiscoveredModels(rawModels, credential);
    }

    private boolean isLocalPassThroughBaseUrl() {
        return AiProviderRuntimeProperties.isLocalBaseUrlValue(baseUrl);
    }

    private String rootGatewayBaseUrl() {
        return baseUrl.endsWith("/anthropic") ? baseUrl.substring(0, baseUrl.length() - "/anthropic".length()) : baseUrl;
    }
}
