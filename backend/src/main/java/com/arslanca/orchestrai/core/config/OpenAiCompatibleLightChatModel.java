package com.arslanca.orchestrai.core.config;

import com.arslanca.orchestrai.core.ai.provider.ProviderUsageMetadataSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arslanca.orchestrai.business.planning.preparation.ChatPlanningHandoffTool;

final class OpenAiCompatibleLightChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLightChatModel.class);

    private final RestClient.Builder lightAiRestClientBuilder;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String modelId;
    private final double temperature;
    private final int maxTokens;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions defaultOptions;

    OpenAiCompatibleLightChatModel(
            final RestClient.Builder lightAiRestClientBuilder,
            final ObjectMapper objectMapper,
            final String baseUrl,
            final String apiKey,
            final String modelId,
            final double temperature,
            final int maxTokens,
            final ToolCallingManager toolCallingManager) {
        this.lightAiRestClientBuilder = lightAiRestClientBuilder;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.toolCallingManager = toolCallingManager;
        this.defaultOptions = ChatOptions.builder()
                .model(modelId)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    @Override
    public ChatResponse call(final Prompt prompt) {
        return call(prompt, 0);
    }

    @Override
    public Flux<ChatResponse> stream(final Prompt prompt) {
        return Flux.defer(() -> Flux.just(call(prompt)));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }

    private ChatResponse call(final Prompt prompt, final int toolCallDepth) {
        final JsonNode response = exchangeCompletion(prompt, false);
        final ChatResponse chatResponse = adaptToolCallsIfNecessary(prompt, toChatResponse(response));

        if (chatResponse.hasToolCalls() && shouldExecuteTools(prompt)) {
            if (toolCallDepth >= 4) {
                throw new IllegalStateException("Local light model exceeded maximum tool-calling depth");
            }

            final ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
            if (shouldShortCircuitAfterToolExecution(chatResponse)) {
                return buildToolDrivenCompletionResponse(chatResponse);
            }
            if (toolExecutionResult.returnDirect()) {
                return new ChatResponse(ToolExecutionResult.buildGenerations(toolExecutionResult));
            }

            final List<Message> nextInstructions = new ArrayList<>(prompt.getInstructions());
            nextInstructions.addAll(toolExecutionResult.conversationHistory());
            return call(new Prompt(nextInstructions, prompt.getOptions()), toolCallDepth + 1);
        }

        return chatResponse;
    }

    private boolean shouldShortCircuitAfterToolExecution(final ChatResponse chatResponse) {
        final AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        if (assistantMessage == null || !assistantMessage.hasToolCalls()) {
            return false;
        }

        return assistantMessage.getToolCalls().stream()
                .map(AssistantMessage.ToolCall::name)
                .allMatch(ChatPlanningHandoffTool.TOOL_NAME::equals);
    }

    private ChatResponse buildToolDrivenCompletionResponse(final ChatResponse chatResponse) {
        final AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        final String content = assistantMessage == null || assistantMessage.getText() == null || assistantMessage.getText().isBlank()
                ? "Planlama tamamlandi. Blueprint generation review hazirlaniyor."
                : assistantMessage.getText();
        final AssistantMessage finalAssistantMessage = new AssistantMessage(content);
        return new ChatResponse(List.of(new Generation(finalAssistantMessage)), chatResponse.getMetadata());
    }

    private ChatResponse adaptToolCallsIfNecessary(final Prompt prompt, final ChatResponse chatResponse) {
        if (!chatResponse.hasToolCalls() || !(prompt.getOptions() instanceof ToolCallingChatOptions toolOptions)) {
            return chatResponse;
        }

        final List<ToolDefinition> toolDefinitions = toolCallingManager.resolveToolDefinitions(toolOptions);
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return chatResponse;
        }

        final Set<String> availableToolNames = new LinkedHashSet<>();
        for (ToolDefinition toolDefinition : toolDefinitions) {
            availableToolNames.add(toolDefinition.name());
        }

        final AssistantMessage originalAssistantMessage = chatResponse.getResult().getOutput();
        final List<AssistantMessage.ToolCall> adaptedToolCalls = new ArrayList<>();
        boolean changed = false;

        for (AssistantMessage.ToolCall toolCall : originalAssistantMessage.getToolCalls()) {
            final String resolvedToolName = resolveToolName(toolCall.name(), availableToolNames);
            if (!resolvedToolName.equals(toolCall.name())) {
                changed = true;
                log.warn("Adapting local light model tool call name from '{}' to '{}'", toolCall.name(), resolvedToolName);
            }
            adaptedToolCalls.add(new AssistantMessage.ToolCall(
                    toolCall.id(),
                    toolCall.type(),
                    resolvedToolName,
                    toolCall.arguments()));
        }

        if (!changed) {
            return chatResponse;
        }

        final AssistantMessage adaptedAssistantMessage = AssistantMessage.builder()
                .content(originalAssistantMessage.getText())
                .toolCalls(adaptedToolCalls)
                .build();

        return new ChatResponse(List.of(new Generation(adaptedAssistantMessage)), chatResponse.getMetadata());
    }

    private String extractText(final JsonNode contentNode) {
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            final StringBuilder builder = new StringBuilder();
            for (JsonNode node : contentNode) {
                if (node.isTextual()) {
                    builder.append(node.asText());
                } else {
                    builder.append(node.path("text").asText(""));
                }
            }
            return builder.toString();
        }
        return "";
    }

    private ChatResponse toChatResponse(final JsonNode response) {
        final JsonNode messageNode = response.path("choices").path(0).path("message");
        final String rawText = normalizeAssistantText(extractText(messageNode.path("content")));
        final List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(messageNode.path("tool_calls"), rawText);
        final String text = toolCalls.isEmpty() ? rawText : "";

        if (text.isBlank() && toolCalls.isEmpty()) {
            throw new IllegalStateException("Local light model returned an empty chat completion response");
        }

        final AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(text)
                .toolCalls(toolCalls)
                .build();
        final ChatResponseMetadata metadata = ProviderUsageMetadataSupport.openAiCompatibleMetadata(response, modelId);
        return new ChatResponse(List.of(new Generation(assistantMessage)), metadata);
    }

    private List<AssistantMessage.ToolCall> extractToolCalls(final JsonNode toolCallsNode, final String rawText) {
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            return mapToolCalls(toolCallsNode);
        }
        return parseInlineToolCalls(rawText);
    }

    private List<AssistantMessage.ToolCall> mapToolCalls(final JsonNode toolCallsNode) {
        final List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            toolCalls.add(new AssistantMessage.ToolCall(
                    toolCallNode.path("id").asText(),
                    toolCallNode.path("type").asText("function"),
                    toolCallNode.path("function").path("name").asText(),
                    toolCallNode.path("function").path("arguments").asText("{}")));
        }
        return toolCalls;
    }

    private List<AssistantMessage.ToolCall> parseInlineToolCalls(final String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        try {
            final JsonNode root = objectMapper.readTree(rawText);
            final JsonNode toolCallsNode = root.path("tool_calls");
            if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
                return List.of();
            }

            final List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
            int index = 0;
            for (JsonNode toolCallNode : toolCallsNode) {
                final String toolName = resolveInlineToolName(toolCallNode);
                if (toolName == null || toolName.isBlank()) {
                    continue;
                }
                final JsonNode argumentsNode = resolveInlineToolArguments(toolCallNode);
                final String arguments = argumentsNode == null || argumentsNode.isMissingNode() || argumentsNode.isNull()
                        ? "{}"
                        : argumentsNode.isTextual() ? argumentsNode.asText() : argumentsNode.toString();
                toolCalls.add(new AssistantMessage.ToolCall(
                        "inline-tool-call-" + index++,
                        "function",
                        toolName,
                        arguments));
            }
            return toolCalls;
        }
        catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeAssistantText(final String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        try {
            final JsonNode root = objectMapper.readTree(rawText);
            if (!root.isObject()) {
                return rawText;
            }

            if (root.has("tool_calls")) {
                return rawText;
            }

            if (root.path("name").asText("").equalsIgnoreCase("assistant") && root.path("content").isTextual()) {
                return root.path("content").asText();
            }

            if (root.path("content").isTextual()) {
                return root.path("content").asText();
            }
        }
        catch (Exception ignored) {
            return rawText;
        }

        return rawText;
    }

    private String resolveInlineToolName(final JsonNode toolCallNode) {
        if (toolCallNode == null || toolCallNode.isMissingNode() || toolCallNode.isNull()) {
            return null;
        }

        if (toolCallNode.path("function").isTextual()) {
            return toolCallNode.path("function").asText();
        }

        final JsonNode functionNode = toolCallNode.path("function");
        if (functionNode.isObject()) {
            return functionNode.path("name").asText(null);
        }

        return toolCallNode.path("name").asText(null);
    }

    private JsonNode resolveInlineToolArguments(final JsonNode toolCallNode) {
        if (toolCallNode == null || toolCallNode.isMissingNode() || toolCallNode.isNull()) {
            return null;
        }

        if (toolCallNode.has("args")) {
            return toolCallNode.path("args");
        }

        final JsonNode functionNode = toolCallNode.path("function");
        if (functionNode.isObject() && functionNode.has("arguments")) {
            return functionNode.path("arguments");
        }

        return null;
    }

    private JsonNode exchangeCompletion(final Prompt prompt, final boolean stream) {
        final List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.TOOL && message instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    final Map<String, Object> toolMessage = new LinkedHashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", response.id());
                    toolMessage.put("name", response.name());
                    toolMessage.put("content", response.responseData());
                    messages.add(toolMessage);
                }
                continue;
            }

            final Map<String, Object> messagePayload = new LinkedHashMap<>();
            messagePayload.put("role", toRole(message.getMessageType()));
            if (message.getMessageType() == MessageType.ASSISTANT && message instanceof AssistantMessage assistantMessage
                    && assistantMessage.hasToolCalls()) {
                messagePayload.put("content", message.getText() == null || message.getText().isBlank() ? null : message.getText());
                final List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                    final Map<String, Object> toolCallPayload = new LinkedHashMap<>();
                    toolCallPayload.put("id", toolCall.id());
                    toolCallPayload.put("type", toolCall.type());
                    toolCallPayload.put("function", Map.of(
                            "name", toolCall.name(),
                            "arguments", toolCall.arguments()));
                    toolCalls.add(toolCallPayload);
                }
                messagePayload.put("tool_calls", toolCalls);
            }
            else {
                messagePayload.put("content", message.getText());
            }
            messages.add(messagePayload);
        }

        final Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        addToolDefinitions(prompt, requestBody);
        if (stream) {
            requestBody.put("stream", true);
        }

        final String rawResponse = lightAiRestClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1/chat/completions")
                .headers(headers -> {
                    headers.setBearerAuth(apiKey);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readTree(rawResponse);
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to parse local light model response", ex);
        }
    }

    private String toRole(final MessageType messageType) {
        return switch (messageType) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            default -> "user";
        };
    }

    private boolean shouldExecuteTools(final Prompt prompt) {
        return prompt.getOptions() instanceof ToolCallingChatOptions options
                && ToolCallingChatOptions.isInternalToolExecutionEnabled(options);
    }

    private String resolveToolName(final String requestedToolName, final Set<String> availableToolNames) {
        if (requestedToolName == null || requestedToolName.isBlank() || availableToolNames.isEmpty()) {
            return requestedToolName;
        }

        if (availableToolNames.contains(requestedToolName)) {
            return requestedToolName;
        }

        final String normalizedRequested = normalizeToolName(requestedToolName);
        for (String availableToolName : availableToolNames) {
            if (normalizeToolName(availableToolName).equals(normalizedRequested)) {
                return availableToolName;
            }
        }

        final List<String> fuzzyMatches = new ArrayList<>();
        for (String availableToolName : availableToolNames) {
            final String normalizedAvailable = normalizeToolName(availableToolName);
            if (normalizedAvailable.contains(normalizedRequested) || normalizedRequested.contains(normalizedAvailable)) {
                fuzzyMatches.add(availableToolName);
            }
        }

        if (fuzzyMatches.size() == 1) {
            return fuzzyMatches.get(0);
        }

        if (availableToolNames.size() == 1) {
            return availableToolNames.iterator().next();
        }

        return requestedToolName;
    }

    private String normalizeToolName(final String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private void addToolDefinitions(final Prompt prompt, final Map<String, Object> requestBody) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions toolOptions)) {
            return;
        }

        final List<ToolDefinition> toolDefinitions = toolCallingManager.resolveToolDefinitions(toolOptions);
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return;
        }

        final List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition toolDefinition : toolDefinitions) {
            final Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", toolDefinition.name());
            function.put("description", toolDefinition.description());
            try {
                function.put("parameters", objectMapper.readValue(toolDefinition.inputSchema(), Map.class));
            }
            catch (Exception exception) {
                throw new IllegalStateException("Failed to parse tool definition schema for local light model", exception);
            }

            final Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", function);
            tools.add(tool);
        }

        requestBody.put("tools", tools);
        requestBody.put("tool_choice", "auto");
    }
}
