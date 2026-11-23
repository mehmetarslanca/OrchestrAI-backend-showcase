package com.arslanca.orchestrai.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrai.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri) {

    public boolean configured() {
        return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
    }

    private boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
