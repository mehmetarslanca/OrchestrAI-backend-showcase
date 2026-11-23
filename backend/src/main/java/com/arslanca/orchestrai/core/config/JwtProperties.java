package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "orchestrai.jwt")
public record JwtProperties(
    @NotBlank(message = "JWT secret cannot be empty. Please configure orchestrai.jwt.secret")
    @jakarta.validation.constraints.Size(min = 32, message = "JWT secret must be at least 32 characters long for HMAC-SHA256")
    String secret,
        @Positive(message = "JWT expiration must be positive")
        long expirationMs,
        @Positive(message = "JWT refresh expiration must be positive")
        long refreshExpirationMs,
        @NotBlank(message = "JWT issuer cannot be empty. Please configure orchestrai.jwt.issuer")
        String issuer) {
}
