package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.data.redis")
public record RedisRuntimeProperties(
        @NotBlank(message = "Redis host must be configured")
        String host,
        @NotNull(message = "Redis port must be configured")
        @Positive(message = "Redis port must be positive")
        Integer port,
        String password) {
}
