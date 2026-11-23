package com.arslanca.orchestrai.core.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.Set;

@Configuration
public class EnvironmentValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentValidationConfig.class);

    private static final Set<String> LEGACY_BACKEND_CALLBACK_PATHS = Set.of(
            "/login/oauth2/code/github",
            "/api/v1/auth/github/callback",
            "/login/oauth2/code/google",
            "/api/v1/auth/google/callback",
            "/api/v1/ai-connections/github/oauth/callback");

    private static final Set<String> OFFICIAL_REMOTE_PROVIDERS = Set.of("google", "openai", "github", "github-copilot", "anthropic");
    private static final Set<String> PRODUCTION_PROFILE_NAMES = Set.of("prod", "production", "live");

    private final GitHubOAuthProperties gitHubOAuthProperties;
    private final GitHubAiOAuthProperties gitHubAiOAuthProperties;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final AiProviderRuntimeProperties aiProviderRuntimeProperties;
    private final Environment environment;

    public EnvironmentValidationConfig(
            final GitHubOAuthProperties gitHubOAuthProperties,
            final GitHubAiOAuthProperties gitHubAiOAuthProperties,
            final GoogleOAuthProperties googleOAuthProperties,
            final AiProviderRuntimeProperties aiProviderRuntimeProperties,
            final Environment environment) {
        this.gitHubOAuthProperties = gitHubOAuthProperties;
        this.gitHubAiOAuthProperties = gitHubAiOAuthProperties;
        this.googleOAuthProperties = googleOAuthProperties;
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
        this.environment = environment;
    }


    @PostConstruct
    public void validateEnvironmentContract() {
        validateClientSideCallbackBridge("GitHub", gitHubOAuthProperties.redirectUri(), "/api/v1/auth/github/callback");
        if (gitHubAiOAuthProperties.configured()) {
            validateClientSideCallbackBridge("GitHub AI", gitHubAiOAuthProperties.redirectUri(), "/api/v1/ai-connections/github/oauth/callback");
        }
        if (googleOAuthProperties.configured()) {
            validateClientSideCallbackBridge("Google", googleOAuthProperties.redirectUri(), "/api/v1/auth/google/callback");
        }

        validateAiProviderRuntimeContract();

        log.info("Runtime contract validation passed.");
    }

    private void validateAiProviderRuntimeContract() {
        if (aiProviderRuntimeProperties.testModeEnabled()) {
            if (environment.acceptsProfiles(Profiles.of(PRODUCTION_PROFILE_NAMES.toArray(String[]::new)))) {
                throw new IllegalStateException("AI test mode cannot be enabled while the prod profile is active.");
            }

            log.warn("============================================================");
            log.warn("AI TEST MODE IS ENABLED");
            log.warn("Local/test-only providers may be exposed and provider base URLs may point to local gateways.");
            log.warn("Disable orchestrai.ai.test-mode.enabled before any production-like deployment.");
            log.warn("============================================================");
            return;
        }

        OFFICIAL_REMOTE_PROVIDERS.forEach(this::validateOfficialProviderBaseUrl);
    }

    private void validateOfficialProviderBaseUrl(final String providerKey) {
        aiProviderRuntimeProperties.baseUrlFor(providerKey)
                .filter(AiProviderRuntimeProperties::isLocalBaseUrlValue)
                .ifPresent(baseUrl -> {
                    throw new IllegalStateException(
                            "Provider base URL for '" + providerKey + "' points to a local address while test mode is disabled: "
                                    + baseUrl);
                });
    }

    private void validateClientSideCallbackBridge(
            final String providerName,
            final String redirectUriValue,
            final String backendCallbackPath) {
        final URI redirectUri = URI.create(redirectUriValue);
        if (!LEGACY_BACKEND_CALLBACK_PATHS.contains(redirectUri.getPath())) {
            return;
        }

        if (environment.acceptsProfiles(Profiles.of("dev", "local", "test"))) {
            log.warn(
                    "Legacy {} redirect URI '{}' is allowed in the active development profile. Application startup will continue locally, but OAuth login will stay misaligned until the redirect URI is moved to a client-side bridge that POSTs the code to {}.",
                    providerName,
                    redirectUri,
                    backendCallbackPath);
            return;
        }

        throw new IllegalStateException(
                providerName + " redirect URI must point to a client-side callback bridge, not the backend callback itself. "
                        + "Configure the provider redirect URI to a frontend/CLI bridge URL that POSTs the code to "
                        + backendCallbackPath + ".");
    }
}
