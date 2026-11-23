package com.arslanca.orchestrai.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "orchestrai.ai.github.oauth")
public record GitHubAiOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizeUri,
        String tokenUri,
        String userApiUri,
        Duration stateTtl,
        List<String> modelsScopes,
        List<String> copilotScopes) {

    public GitHubAiOAuthProperties {
        authorizeUri = hasText(authorizeUri) ? authorizeUri : "https://github.com/login/oauth/authorize";
        tokenUri = hasText(tokenUri) ? tokenUri : "https://github.com/login/oauth/access_token";
        userApiUri = hasText(userApiUri) ? userApiUri : "https://api.github.com/user";
        stateTtl = stateTtl == null || stateTtl.isZero() || stateTtl.isNegative() ? Duration.ofMinutes(10) : stateTtl;
        modelsScopes = normalizeScopes(modelsScopes, List.of("read:user", "user:email"));
        copilotScopes = normalizeScopes(copilotScopes, List.of("read:user", "user:email"));
    }

    public boolean configured() {
        return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> normalizeScopes(final List<String> scopes, final List<String> fallback) {
        final List<String> effectiveScopes = scopes == null ? fallback : scopes.stream()
                .filter(GitHubAiOAuthProperties::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        return effectiveScopes.isEmpty() ? List.copyOf(fallback) : List.copyOf(effectiveScopes);
    }
}
