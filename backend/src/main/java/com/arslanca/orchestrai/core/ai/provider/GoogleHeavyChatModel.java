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

final class GoogleHeavyChatModel implements ChatModel {

    private final RestClient.Builder heavyAiRestClientBuilder;
    private final String baseUrl;
    private final ProviderCredential credential;
    private final String modelId;
    private final double temperature;
    private final int maxTokens;
    private final ChatOptions defaultOptions;

    GoogleHeavyChatModel(
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

        final List<Map<String, Object>> contents = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.SYSTEM || message.getMessageType() == MessageType.TOOL) {
                continue;
            }
            contents.add(Map.of(
                    "role", message.getMessageType() == MessageType.ASSISTANT ? "model" : "user",
                    "parts", List.of(Map.of("text", message.getText()))));
        }

        final Map<String, Object> requestBody = new LinkedHashMap<>();
        if (systemText != null && !systemText.isBlank()) {
            requestBody.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemText))));
        }
        requestBody.put("contents", contents);
        requestBody.put("generationConfig", Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxTokens));

        final JsonNode response = heavyAiRestClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1beta/models/" + modelId + ":generateContent?key=" + credential.apiKey())
                .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        final JsonNode partsNode = response.path("candidates").path(0).path("content").path("parts");
        if (!partsNode.isArray() || partsNode.isEmpty()) {
            throw new IllegalStateException("Provider returned an empty Gemini response");
        }

        final StringBuilder builder = new StringBuilder();
        for (JsonNode part : partsNode) {
            builder.append(part.path("text").asText(""));
        }

        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(builder.toString()))),
                ProviderUsageMetadataSupport.googleMetadata(response, modelId));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }
}
