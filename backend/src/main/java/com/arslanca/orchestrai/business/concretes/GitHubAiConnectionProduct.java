package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.core.config.GitHubAiOAuthProperties;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.util.List;
import java.util.Locale;

enum GitHubAiConnectionProduct {
    MODELS("models", AiConnectionType.GITHUB_MODELS_USER_LINKED, "GitHub Models") {
        @Override
        List<String> scopes(final GitHubAiOAuthProperties properties) {
            return properties.modelsScopes();
        }

        @Override
        String runtimeTarget() {
            return "github-models";
        }

        @Override
        AiConnectionStatus authorizationFailureStatus() {
            return AiConnectionStatus.ENTITLEMENT_MISSING;
        }
    },
    COPILOT("copilot", AiConnectionType.GITHUB_COPILOT_USER_LINKED, "GitHub Copilot") {
        @Override
        List<String> scopes(final GitHubAiOAuthProperties properties) {
            return properties.copilotScopes();
        }

        @Override
        String runtimeTarget() {
            return "github-copilot";
        }

        @Override
        AiConnectionStatus authorizationFailureStatus() {
            return AiConnectionStatus.DEGRADED;
        }
    };

    private final String surfaceKey;
    private final AiConnectionType connectionType;
    private final String displayName;

    GitHubAiConnectionProduct(final String surfaceKey, final AiConnectionType connectionType, final String displayName) {
        this.surfaceKey = surfaceKey;
        this.connectionType = connectionType;
        this.displayName = displayName;
    }

    static GitHubAiConnectionProduct fromSurface(final String rawSurface) {
        final String normalizedSurface = rawSurface == null ? "" : rawSurface.trim().toLowerCase(Locale.ROOT);
        for (GitHubAiConnectionProduct product : values()) {
            if (product.surfaceKey.equals(normalizedSurface)) {
                return product;
            }
        }
        throw new IllegalArgumentException("Desteklenmeyen GitHub AI surface: " + rawSurface);
    }

    static GitHubAiConnectionProduct fromConnectionType(final AiConnectionType connectionType) {
        for (GitHubAiConnectionProduct product : values()) {
            if (product.connectionType == connectionType) {
                return product;
            }
        }
        throw new IllegalArgumentException("Desteklenmeyen GitHub AI connection type: " + connectionType);
    }

    String surfaceKey() {
        return surfaceKey;
    }

    AiConnectionType connectionType() {
        return connectionType;
    }

    String displayName() {
        return displayName;
    }

    abstract List<String> scopes(GitHubAiOAuthProperties properties);

    abstract String runtimeTarget();

    abstract AiConnectionStatus authorizationFailureStatus();
}
