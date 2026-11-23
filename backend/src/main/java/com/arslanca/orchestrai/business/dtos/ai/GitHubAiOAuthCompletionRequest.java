package com.arslanca.orchestrai.business.dtos.ai;

import jakarta.validation.constraints.NotBlank;

public record GitHubAiOAuthCompletionRequest(
        @NotBlank(message = "completionToken bos olamaz.") String completionToken) {
}
