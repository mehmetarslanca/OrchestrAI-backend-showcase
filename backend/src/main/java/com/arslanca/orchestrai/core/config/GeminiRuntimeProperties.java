package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.ai.google.genai")
public record GeminiRuntimeProperties(
        @NotBlank(message = "Gemini API key must be configured")
        String apiKey) {
}
