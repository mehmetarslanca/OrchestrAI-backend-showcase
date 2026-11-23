package com.arslanca.orchestrai.core.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AnthropicHeavyChatModel implements ChatModel {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient.Builder heavyAiRestClientBuilder;
    private final String baseUrl;
    private final ProviderCredential credential;
    private final String modelId;
    private final double temperature;
    private final int maxTokens;
    private final ChatOptions defaultOptions;

    AnthropicHeavyChatModel(
            final RestClient.Builder heavyAiRestClientBuilder,
            final String baseUrl,
            final ProviderCredential credential,
            final String modelId,
            final double temperature,
            final int maxTokens) {
        this.heavyAiRestClientBuilder = heavyAiRestClientBuilder;
        this.baseUrl = baseUrl;
        this.credential = credential;
        this.modelId = modelId;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.defaultOptions = ChatOptions.builder()
                .model(modelId)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    @Override
    public ChatResponse call(final Prompt prompt) {
        final String systemText = prompt.getSystemMessages().stream()
                .map(Message::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(null);

        final List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.SYSTEM || message.getMessageType() == MessageType.TOOL) {
                continue;
            }
            messages.add(Map.of(
                    "role", message.getMessageType() == MessageType.ASSISTANT ? "assistant" : "user",
                    "content", List.of(Map.of("type", "text", "text", message.getText()))));
        }

        final Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        if (systemText != null && !systemText.isBlank()) {
            requestBody.put("system", systemText);
        }

        final JsonNode response = heavyAiRestClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1/messages")
                .headers(headers -> {
                    headers.add("x-api-key", credential.apiKey());
                    headers.add("anthropic-version", ANTHROPIC_VERSION);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        final JsonNode contentNode = response.path("content");
        if (!contentNode.isArray() || contentNode.isEmpty()) {
            throw new IllegalStateException("Provider returned an empty Anthropic response");
        }

        final StringBuilder builder = new StringBuilder();
        for (JsonNode node : contentNode) {
            if ("text".equals(node.path("type").asText())) {
                builder.append(node.path("text").asText(""));
            }
        }

        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(builder.toString()))),
                ProviderUsageMetadataSupport.anthropicMetadata(response, modelId));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }
}
