package com.arslanca.orchestrai.business.usage;

public record AiTokenUsage(
        long inputTokens,
        long outputTokens,
        long cachedInputTokens,
        long reasoningTokens) {

    public AiTokenUsage {
        if (inputTokens < 0 || outputTokens < 0 || cachedInputTokens < 0 || reasoningTokens < 0) {
            throw new IllegalArgumentException("AI token usage values cannot be negative.");
        }
    }
}
