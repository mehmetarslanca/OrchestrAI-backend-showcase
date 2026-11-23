package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractGitHubUserLinkedHeavyProviderAdapter extends AbstractRestHeavyProviderAdapter {

    private static final String GITHUB_ACCEPT_HEADER = "application/vnd.github+json";
    private static final String GITHUB_API_VERSION = "2026-03-10";

    private final String baseUrl;

    protected AbstractGitHubUserLinkedHeavyProviderAdapter(
            final String baseUrl,
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties) {
        super(platformCredentialRegistry, heavyAiRestClientBuilder, objectMapper, aiModelProperties);
        this.baseUrl = baseUrl;
    }

    @Override
    public String providerKey() {
        return "github";
    }

    @Override
    public boolean supportsPlatformCredential() {
        return false;
    }

    @Override
    public boolean supports(final ResolvedHeavyModel resolvedModel) {
        return resolvedModel != null && supportedConnectionType() == resolvedModel.connectionType();
    }

    @Override
    public boolean supports(final ProviderCredential credential) {
        return credential != null && supportedConnectionType() == credential.connectionType();
    }

    @Override
    public List<DiscoveredHeavyModel> listAvailableHeavyModels(final ProviderCredential credential) {
        final JsonNode response = getJson(baseUrl + "/catalog/models", headers -> {
            headers.setBearerAuth(credential.apiKey());
            headers.setAccept(List.of(MediaType.valueOf(GITHUB_ACCEPT_HEADER)));
            headers.set("X-GitHub-Api-Version", GITHUB_API_VERSION);
        });

        final List<DiscoveredHeavyModel> discoveredModels = new ArrayList<>();
        if (!response.isArray()) {
            return List.of();
        }

        for (JsonNode node : response) {
            if (!supportsCatalogNode(node)) {
                continue;
            }
            final String modelId = readText(node, "id");
            if (modelId == null || modelId.isBlank()) {
                continue;
            }
            final ApprovedHeavyModelRegistry.ApprovedFamily approvedFamily = ApprovedHeavyModelRegistry
                    .normalize("openai", modelId)
                    .orElse(ApprovedHeavyModelRegistry.normalize("anthropic", modelId)
                            .orElse(ApprovedHeavyModelRegistry.normalize("google", modelId).orElse(null)));
            final String family = approvedFamily == null
                    ? ApprovedHeavyModelRegistry.normalizeKey(modelId)
                    : approvedFamily.family();
            final String displayName = readText(node, "name") != null && !readText(node, "name").isBlank()
                    ? readText(node, "name")
                    : approvedFamily == null ? modelId : approvedFamily.displayName();
            discoveredModels.add(new DiscoveredHeavyModel(
                    providerKey(),
                    family,
                    modelId,
                    displayName,
                    credential.source(),
                    credential.credentialOwner(),
                    credential.selectedConnectionId(),
                    null,
                    supportedConnectionType()));
        }
        return discoveredModels.stream()
                .sorted(java.util.Comparator
                        .comparingInt((DiscoveredHeavyModel model) -> ApprovedHeavyModelRegistry.providerOrder(model.provider()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.familyOrder("openai", model.family()))
                        .thenComparing(model -> model.displayName() == null ? "" : model.displayName()))
                .toList();
    }

    @Override
    public ChatModel buildHeavyChatModel(final ResolvedHeavyModel resolvedModel, final ProviderCredential credential) {
        return new GitHubInferenceHeavyChatModel(
                heavyAiRestClientBuilder,
                objectMapper,
                baseUrl,
                credential,
                resolvedModel.concreteModelId(),
                aiModelProperties.heavyModelTemperature(),
                aiModelProperties.heavyModelMaxTokens());
    }

    protected boolean supportsCatalogNode(final JsonNode node) {
        return containsTextModality(node.path("supported_input_modalities"))
                && containsTextModality(node.path("supported_output_modalities"));
    }

    private boolean containsTextModality(final JsonNode modalities) {
        if (!modalities.isArray() || modalities.isEmpty()) {
            return true;
        }
        for (JsonNode modality : modalities) {
            if ("text".equalsIgnoreCase(modality.asText())) {
                return true;
            }
        }
        return false;
    }

    protected abstract AiConnectionType supportedConnectionType();
}
