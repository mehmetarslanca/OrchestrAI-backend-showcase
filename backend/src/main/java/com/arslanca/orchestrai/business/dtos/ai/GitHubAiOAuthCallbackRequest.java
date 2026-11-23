package com.arslanca.orchestrai.business.dtos.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubAiOAuthCallbackRequest(
        String code,
        String state,
        String error,
        @JsonProperty("error_description") String errorDescription) {
}
