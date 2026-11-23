package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for the dual-LLM orchestration pipeline.
 *
 * <p>
 * Properties are bound from {@code orchestrai.ai.*} in application.properties.
 * Model names are provider-agnostic — changing models or providers requires
 * only a properties change, no code modifications.
 * </p>
 *
 * <p>
 * Registered via {@code @EnableConfigurationProperties} in
 * {@link com.arslanca.orchestrai.OrchestraiApplication}, consistent with
 * {@link JwtProperties} and {@link GitHubOAuthProperties}.
 * </p>
 */
@Validated
@ConfigurationProperties(prefix = "orchestrai.ai")
public record AiModelProperties(
        /** Model identifier for the lightweight/fast LLM (Stage 1: Router/Analyzer). */
        @NotBlank(message = "Light model must be configured")
        String lightModel,
        /**
         * Temperature for the light model. Lower values produce more deterministic
         * outputs.
         */
        double lightModelTemperature,
        /** Maximum tokens for the light model response. */
        @Positive(message = "Light model max tokens must be positive")
        int lightModelMaxTokens,
        /**
         * Preferred platform heavy model key in provider:family format.
         * Runtime resolution may fall back to another available approved model.
         */
        @NotBlank(message = "Preferred heavy model key must be configured")
        String heavyModel,
        /** Temperature for the heavy model. */
        double heavyModelTemperature,
        /** Maximum tokens for the heavy model response. */
        @Positive(message = "Heavy model max tokens must be positive")
        int heavyModelMaxTokens) {
}
