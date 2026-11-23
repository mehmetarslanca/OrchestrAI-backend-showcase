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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(0)
public class GoogleHeavyProviderAdapter extends AbstractRestHeavyProviderAdapter {

    private final String baseUrl;

    public GoogleHeavyProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties,
            @Value("${orchestrai.ai.providers.google.base-url:https://generativelanguage.googleapis.com}") final String baseUrl) {
        super(platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
        this.baseUrl = baseUrl;
    }

    @Override
    public String providerKey() {
        return "google";
    }

    @Override
    public List<DiscoveredHeavyModel> listAvailableHeavyModels(final ProviderCredential credential) {
        try {
            final JsonNode response = getJson(baseUrl + "/v1beta/models?key=" + credential.apiKey(), headers -> {
            });
            final List<RawProviderModel> rawModels = new ArrayList<>();
            for (JsonNode node : response.path("models")) {
                final String modelId = readText(node, "name");
                final String displayName = readText(node, "displayName");
                if (isSupportedGoogleTextModel(node, modelId)) {
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

        return new GoogleHeavyChatModel(
                heavyAiRestClientBuilder,
                baseUrl,
                credential,
                resolvedModel.concreteModelId(),
                aiModelProperties.heavyModelTemperature(),
                aiModelProperties.heavyModelMaxTokens());
    }

    private boolean isSupportedGoogleTextModel(final JsonNode node, final String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }

        final Set<String> supportedMethods = new HashSet<>();
        for (JsonNode methodNode : node.path("supportedGenerationMethods")) {
            if (methodNode.isTextual()) {
                supportedMethods.add(methodNode.asText());
            }
        }
        if (!supportedMethods.contains("generateContent")) {
            return false;
        }

        final String normalizedModelId = ApprovedHeavyModelRegistry.normalizeKey(modelId);
        if (!normalizedModelId.startsWith("gemini-")) {
            return false;
        }
        if (normalizedModelId.endsWith("-latest")) {
            return false;
        }
        return !normalizedModelId.contains("embedding")
                && !normalizedModelId.contains("image")
                && !normalizedModelId.contains("tts")
                && !normalizedModelId.contains("audio")
                && !normalizedModelId.contains("live")
                && !normalizedModelId.contains("robotics")
                && !normalizedModelId.contains("computer-use")
                && !normalizedModelId.contains("customtools")
                && !normalizedModelId.contains("deep-research");
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
        return baseUrl.endsWith("/gemini") ? baseUrl.substring(0, baseUrl.length() - "/gemini".length()) : baseUrl;
    }
}
