package com.arslanca.orchestrai.core.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenAiCompatibleHeavyChatModel implements ChatModel {

    private final RestClient.Builder heavyAiRestClientBuilder;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final ProviderCredential credential;
    private final String modelId;
    private final double temperature;
    private final int maxTokens;
    private final ChatOptions defaultOptions;

    OpenAiCompatibleHeavyChatModel(
            final RestClient.Builder heavyAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final String baseUrl,
            final ProviderCredential credential,
            final String modelId,
            final double temperature,
            final int maxTokens) {
        this.heavyAiRestClientBuilder = heavyAiRestClientBuilder;
        this.objectMapper = objectMapper;
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
        final JsonNode response = exchangeCompletion(prompt, false);

        final String text = extractText(response);
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ProviderUsageMetadataSupport.openAiCompatibleMetadata(response, modelId));
    }

    @Override
    public Flux<ChatResponse> stream(final Prompt prompt) {
        return Flux.defer(() -> Flux.just(call(prompt)));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }

    private String extractText(final JsonNode response) {
        final JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            final StringBuilder builder = new StringBuilder();
            for (JsonNode node : contentNode) {
                if (node.isTextual()) {
                    builder.append(node.asText());
                } else {
                    final String text = node.path("text").asText("");
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        throw new IllegalStateException("Provider returned an empty chat completion response");
    }

    private JsonNode exchangeCompletion(final Prompt prompt, final boolean stream) {
        final List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.TOOL) {
                continue;
            }
            messages.add(Map.of(
                    "role", toRole(message.getMessageType()),
                    "content", message.getText()));
        }

        final Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        if (stream) {
            requestBody.put("stream", true);
        }

        final String rawResponse = heavyAiRestClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1/chat/completions")
                .headers(headers -> {
                    headers.setBearerAuth(credential.apiKey());
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readTree(rawResponse);
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to parse provider chat completion response", ex);
        }
    }

    private String toRole(final MessageType messageType) {
        return switch (messageType) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            default -> "user";
        };
    }
}
