package com.arslanca.orchestrai.core.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;

public final class ProviderUsageMetadataSupport {

    private ProviderUsageMetadataSupport() {
    }

    public static ChatResponseMetadata openAiCompatibleMetadata(final JsonNode response, final String fallbackModelId) {
        final JsonNode usageNode = response.path("usage");
        final Integer promptTokens = integerValue(usageNode.path("prompt_tokens"));
        final Integer completionTokens = integerValue(usageNode.path("completion_tokens"));
        final Integer totalTokens = integerValue(usageNode.path("total_tokens"));
        final long cachedInputTokens = longValue(usageNode.path("prompt_tokens_details").path("cached_tokens"));
        final long reasoningTokens = firstPositive(
                longValue(usageNode.path("completion_tokens_details").path("reasoning_tokens")),
                longValue(usageNode.path("output_tokens_details").path("reasoning_tokens")));
        return metadataBuilder(response.path("id").asText(null), response.path("model").asText(fallbackModelId),
                promptTokens, completionTokens, totalTokens, cachedInputTokens, reasoningTokens, usageNode);
    }

    public static ChatResponseMetadata anthropicMetadata(final JsonNode response, final String fallbackModelId) {
        final JsonNode usageNode = response.path("usage");
        final Integer promptTokens = integerValue(usageNode.path("input_tokens"));
        final Integer completionTokens = integerValue(usageNode.path("output_tokens"));
        final Integer totalTokens = sum(promptTokens, completionTokens);
        final long cachedInputTokens = longValue(usageNode.path("cache_read_input_tokens"))
                + longValue(usageNode.path("cache_creation_input_tokens"));
        return metadataBuilder(response.path("id").asText(null), response.path("model").asText(fallbackModelId),
                promptTokens, completionTokens, totalTokens, cachedInputTokens, 0L, usageNode);
    }

    public static ChatResponseMetadata googleMetadata(final JsonNode response, final String fallbackModelId) {
        final JsonNode usageNode = response.path("usageMetadata");
        final Integer promptTokens = integerValue(usageNode.path("promptTokenCount"));
        final Integer completionTokens = integerValue(usageNode.path("candidatesTokenCount"));
        final Integer totalTokens = integerValue(usageNode.path("totalTokenCount"));
        final long cachedInputTokens = longValue(usageNode.path("cachedContentTokenCount"));
        final long reasoningTokens = longValue(usageNode.path("thoughtsTokenCount"));
        return metadataBuilder(response.path("responseId").asText(null), response.path("modelVersion").asText(fallbackModelId),
                promptTokens, completionTokens, totalTokens, cachedInputTokens, reasoningTokens, usageNode);
    }

    private static ChatResponseMetadata metadataBuilder(
            final String id,
            final String model,
            final Integer promptTokens,
            final Integer completionTokens,
            final Integer totalTokens,
            final long cachedInputTokens,
            final long reasoningTokens,
            final JsonNode nativeUsage) {
        final ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder()
                .id(blankToNull(id))
                .model(blankToNull(model));
        if (promptTokens != null || completionTokens != null || totalTokens != null) {
            builder.usage(DefaultUsage.fromJson(promptTokens, completionTokens, totalTokens, nativeUsage));
        }
        if (cachedInputTokens > 0) {
            builder.keyValue("cachedInputTokens", cachedInputTokens);
        }
        if (reasoningTokens > 0) {
            builder.keyValue("reasoningTokens", reasoningTokens);
        }
        return builder.build();
    }

    private static Integer integerValue(final JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.canConvertToInt() ? node.asInt() : null;
    }

    private static long longValue(final JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.canConvertToLong()) {
            return 0L;
        }
        return Math.max(0L, node.asLong());
    }

    private static Integer sum(final Integer left, final Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return Math.max(0, (left == null ? 0 : left) + (right == null ? 0 : right));
    }

    private static long firstPositive(final long primary, final long fallback) {
        return primary > 0 ? primary : Math.max(0L, fallback);
    }

    private static String blankToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
