package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "orchestrai.pipeline")
public record PipelineProperties(
    @Positive(message = "Pipeline maxConcurrent must be positive")
    int maxConcurrent
) {}
