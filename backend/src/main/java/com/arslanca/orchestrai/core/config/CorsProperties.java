package com.arslanca.orchestrai.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "orchestrai.cors")
public record CorsProperties(
    List<String> allowedOrigins
) {}
