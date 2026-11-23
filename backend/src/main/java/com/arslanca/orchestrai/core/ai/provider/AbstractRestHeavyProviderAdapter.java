package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

abstract class AbstractRestHeavyProviderAdapter implements HeavyProviderAdapter {

    protected final PlatformCredentialRegistry platformCredentialRegistry;
    protected final RestClient.Builder heavyAiRestClientBuilder;
    protected final ObjectMapper objectMapper;
    protected final AiModelProperties aiModelProperties;

    protected AbstractRestHeavyProviderAdapter(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final AiModelProperties aiModelProperties) {
        this.platformCredentialRegistry = platformCredentialRegistry;
        this.heavyAiRestClientBuilder = heavyAiRestClientBuilder;
        this.objectMapper = objectMapper;
        this.aiModelProperties = aiModelProperties;
    }

    @Override
    public boolean supportsPlatformCredential() {
        return platformCredentialRegistry.hasPlatformCredential(providerKey());
    }

    @Override
    public void validateCredential(final ProviderCredential credential) {
        listAvailableHeavyModels(credential);
    }

    protected ProviderCredential requirePlatformCredential() {
        return platformCredentialRegistry.findPlatformCredential(providerKey())
                .orElseThrow(() -> new IllegalStateException("Platform credential is not configured for provider: "
                        + providerKey()));
    }

    protected JsonNode getJson(final String url, final Consumer<org.springframework.http.HttpHeaders> headersCustomizer) {
        final String response = heavyAiRestClientBuilder.build()
                .get()
                .uri(url)
                .headers(headersCustomizer)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON response from provider", e);
        }
    }

    protected JsonNode postJson(
            final String url,
            final Object requestBody,
            final Consumer<org.springframework.http.HttpHeaders> headersCustomizer) {
        final String response = heavyAiRestClientBuilder.build()
                .post()
                .uri(url)
                .headers(headersCustomizer)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON response from provider", e);
        }
    }

    protected List<DiscoveredHeavyModel> normalizeDiscoveredModels(
            final List<RawProviderModel> rawModels,
            final ProviderCredential credential) {
        final Map<String, DiscoveredHeavyModel> discoveredByFamily = new LinkedHashMap<>();

        for (RawProviderModel rawModel : rawModels) {
            final ApprovedHeavyModelRegistry.ApprovedFamily approvedFamily = ApprovedHeavyModelRegistry
                    .normalize(providerKey(), rawModel.modelId())
                    .orElse(null);
            final String family = approvedFamily == null
                    ? ApprovedHeavyModelRegistry.normalizeKey(rawModel.modelId())
                    : approvedFamily.family();
            final String displayName = approvedFamily != null
                    ? approvedFamily.displayName()
                    : rawModel.displayName() == null || rawModel.displayName().isBlank()
                            ? rawModel.modelId()
                            : rawModel.displayName();

            discoveredByFamily.putIfAbsent(family, new DiscoveredHeavyModel(
                    providerKey(),
                    family,
                    rawModel.modelId(),
                    displayName,
                    credential.source(),
                    credential.credentialOwner(),
                    credential.selectedConnectionId(),
                    null,
                    credential.connectionType()
            ));
        }

        return discoveredByFamily.values().stream()
                .sorted(Comparator
                        .comparingInt((DiscoveredHeavyModel model) -> ApprovedHeavyModelRegistry.providerOrder(model.provider()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.familyOrder(model.provider(), model.family()))
                        .thenComparingInt(model -> ApprovedHeavyModelRegistry.sourceOrder(model.source())))
                .toList();
    }

    protected String readText(final JsonNode node, final String fieldName) {
        final JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    protected record RawProviderModel(String modelId, String displayName) {
    }
}
