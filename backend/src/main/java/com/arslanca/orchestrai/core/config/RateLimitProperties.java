package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "orchestrai.rate-limit")
public record RateLimitProperties(
    @Positive(message = "Rate limit requestsPerMinute must be positive")
    int requestsPerMinute,
    @Positive(message = "Rate limit burstCapacity must be positive")
    int burstCapacity,
    @Positive(message = "Rate limit bucketTtlMinutes must be positive")
    int bucketTtlMinutes,
    boolean failOpen
) {}
