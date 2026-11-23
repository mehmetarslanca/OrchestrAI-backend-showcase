package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "orchestrai.github")
public record GitHubOAuthProperties(
        @NotBlank(message = "GitHub clientId cannot be empty. Please configure orchestrai.github.client-id")
        String clientId,
        @NotBlank(message = "GitHub clientSecret cannot be empty. Please configure orchestrai.github.client-secret")
        String clientSecret,
        @NotBlank(message = "GitHub redirectUri cannot be empty. Please configure orchestrai.github.redirect-uri")
        String redirectUri) {
}
