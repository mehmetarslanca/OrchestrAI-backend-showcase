package com.arslanca.orchestrai.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentValidationConfigTest {

    @Test
    void shouldFailWhenProdProfileEnablesTestMode() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        EnvironmentValidationConfig config = new EnvironmentValidationConfig(
                githubOAuthProperties(),
                gitHubAiOAuthProperties(),
                googleOAuthProperties(),
                runtimeProperties(true, Map.of()),
                environment);

        assertThatThrownBy(config::validateEnvironmentContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test mode");
    }

    @Test
    void shouldFailWhenOfficialProviderUsesLocalBaseUrlOutsideTestMode() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        EnvironmentValidationConfig config = new EnvironmentValidationConfig(
                githubOAuthProperties(),
                gitHubAiOAuthProperties(),
                googleOAuthProperties(),
                runtimeProperties(false, Map.of(
                        "openai", new AiProviderRuntimeProperties.ProviderRuntimeProperties("http://localhost:4000"))),
                environment);

        assertThatThrownBy(config::validateEnvironmentContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local address");
    }

    @Test
    void shouldFailWhenOfficialProviderUsesLoopbackAliasOutsideTestMode() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        EnvironmentValidationConfig config = new EnvironmentValidationConfig(
                githubOAuthProperties(),
                gitHubAiOAuthProperties(),
                googleOAuthProperties(),
                runtimeProperties(false, Map.of(
                        "openai", new AiProviderRuntimeProperties.ProviderRuntimeProperties("http://127.0.0.2:4000"))),
                environment);

        assertThatThrownBy(config::validateEnvironmentContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local address");
    }

    @Test
    void shouldAllowDevProfileWhenTestModeIsEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        EnvironmentValidationConfig config = new EnvironmentValidationConfig(
                githubOAuthProperties(),
                gitHubAiOAuthProperties(),
                googleOAuthProperties(),
                runtimeProperties(true, Map.of()),
                environment);

        assertThatCode(config::validateEnvironmentContract).doesNotThrowAnyException();
    }

    private GitHubOAuthProperties githubOAuthProperties() {
        return new GitHubOAuthProperties("client", "secret", "http://localhost:3000/auth/github/callback");
    }

    private GoogleOAuthProperties googleOAuthProperties() {
        return new GoogleOAuthProperties("client", "secret", "http://localhost:3000/auth/google/callback");
    }

    private GitHubAiOAuthProperties gitHubAiOAuthProperties() {
        return new GitHubAiOAuthProperties(
                "client",
                "secret",
                "http://localhost:3000/auth/github-ai/callback",
                "https://github.com/login/oauth/authorize",
                "https://github.com/login/oauth/access_token",
                "https://api.github.com/user",
                java.time.Duration.ofMinutes(10),
                java.util.List.of("read:user", "user:email"),
                java.util.List.of("read:user", "user:email"));
    }

    private AiProviderRuntimeProperties runtimeProperties(
            final boolean testModeEnabled,
            final Map<String, AiProviderRuntimeProperties.ProviderRuntimeProperties> providers) {
        return new AiProviderRuntimeProperties(
                new AiProviderRuntimeProperties.TestModeProperties(testModeEnabled),
                providers);
    }
}
