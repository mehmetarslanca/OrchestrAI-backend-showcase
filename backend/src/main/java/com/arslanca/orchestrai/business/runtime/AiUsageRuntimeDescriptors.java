package com.arslanca.orchestrai.business.runtime;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.util.UUID;

public final class AiUsageRuntimeDescriptors {

    private AiUsageRuntimeDescriptors() {
    }

    public static AiUsageRuntimeDescriptor forResolvedHeavyModel(
            final ResolvedHeavyModel resolvedHeavyModel,
            final ProviderCredential providerCredential) {
        if (resolvedHeavyModel == null) {
            throw new IllegalArgumentException("resolvedHeavyModel must be provided.");
        }

        final boolean runtimeProviderDiffersFromSelectedProvider = resolvedHeavyModel.runtimeProvider() != null
                && !resolvedHeavyModel.runtimeProvider().equalsIgnoreCase(resolvedHeavyModel.provider());
        final AiConnectionBillingMode billingMode = billingModeFor(
                providerCredential == null ? resolvedHeavyModel.source() : providerCredential.source());
        final String runtimeProviderKey = firstText(
                resolvedHeavyModel.runtimeProvider(),
                providerCredential == null ? null : providerCredential.providerKey(),
                resolvedHeavyModel.provider());

        final AiConnectionType connectionType = runtimeProviderDiffersFromSelectedProvider
                ? connectionTypeFor(runtimeProviderKey, billingMode)
                : firstConnectionType(
                        providerCredential == null ? null : providerCredential.connectionType(),
                        resolvedHeavyModel.connectionType(),
                        connectionTypeFor(runtimeProviderKey, billingMode));

        final String concreteModelId = firstText(
                runtimeProviderDiffersFromSelectedProvider ? resolvedHeavyModel.runtimeTarget() : null,
                resolvedHeavyModel.concreteModelId(),
                resolvedHeavyModel.runtimeTarget());
        final String modelKey = normalizeModelKey(runtimeProviderDiffersFromSelectedProvider
                ? firstText(resolvedHeavyModel.runtimeTarget(), resolvedHeavyModel.family(), resolvedHeavyModel.modelKey())
                : firstText(resolvedHeavyModel.family(), resolvedHeavyModel.modelKey(), resolvedHeavyModel.runtimeTarget()));
        final UUID connectionId = providerCredential == null
                ? resolvedHeavyModel.selectedConnectionId()
                : providerCredential.selectedConnectionId();

        return new AiUsageRuntimeDescriptor(
                connectionId,
                runtimeProviderKey,
                connectionType,
                billingMode,
                modelKey,
                concreteModelId);
    }

    public static AiUsageRuntimeDescriptor forPlatformLightModel(
            final String providerKey,
            final String normalizedModelKey,
            final String concreteModelId) {
        return new AiUsageRuntimeDescriptor(
                null,
                providerKey,
                connectionTypeFor(providerKey, AiConnectionBillingMode.PLATFORM_PAID),
                AiConnectionBillingMode.PLATFORM_PAID,
                normalizeModelKey(normalizedModelKey),
                concreteModelId);
    }

    private static AiConnectionBillingMode billingModeFor(final CredentialSource source) {
        if (source == null) {
            return AiConnectionBillingMode.PLATFORM_PAID;
        }
        return switch (source) {
            case PLATFORM -> AiConnectionBillingMode.PLATFORM_PAID;
            case BYOK -> AiConnectionBillingMode.BYOK;
            case USER_LINKED -> AiConnectionBillingMode.USER_LINKED;
        };
    }

    private static AiConnectionType connectionTypeFor(
            final String providerKey,
            final AiConnectionBillingMode billingMode) {
        if (providerKey == null) {
            throw new IllegalStateException("Runtime providerKey could not be resolved.");
        }
        return switch (providerKey) {
            case "openai" -> billingMode == AiConnectionBillingMode.PLATFORM_PAID
                    ? AiConnectionType.OPENAI_APP_PAID
                    : AiConnectionType.OPENAI_BYOK;
            case "google" -> AiConnectionType.GOOGLE_BYOK;
            case "anthropic" -> AiConnectionType.ANTHROPIC_BYOK;
            case "ollama" -> AiConnectionType.OLLAMA_BYOK;
            case "openai-compatible" -> AiConnectionType.OPENAI_COMPATIBLE_BYOK;
            case "github" -> billingMode == AiConnectionBillingMode.USER_LINKED
                    ? AiConnectionType.GITHUB_MODELS_USER_LINKED
                    : AiConnectionType.OPENAI_CODEX_CHATGPT;
            case "moonshot" -> AiConnectionType.MOONSHOT_BYOK;
            case "zai" -> AiConnectionType.ZAI_BYOK;
            case "xai" -> AiConnectionType.XAI_BYOK;
            default -> throw new IllegalStateException("Unsupported runtime providerKey: " + providerKey);
        };
    }

    private static AiConnectionType firstConnectionType(final AiConnectionType... values) {
        for (AiConnectionType value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeModelKey(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Runtime model key could not be resolved.");
        }
        final String trimmed = value.trim();
        final int separatorIndex = trimmed.indexOf(':');
        return separatorIndex > -1 ? trimmed.substring(separatorIndex + 1) : trimmed;
    }

    private static String firstText(final String... values) {
        for (String value : values) {
            if (value != null) {
                final String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }
}
