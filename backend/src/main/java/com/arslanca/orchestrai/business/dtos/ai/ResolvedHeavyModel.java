package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResolvedHeavyModel(
        String modelKey,
        String provider,
        String family,
        CredentialSource source,
        String credentialOwner,
        String concreteModelId,
        String displayName,
        boolean testOnly,
        boolean emulated,
        String gateway,
        String runtimeProvider,
        String runtimeTarget,
        List<String> badges,
        ModelCapability capability,
        UUID selectedConnectionId,
        AiConnectionExecutionClass executionClass,
        AiConnectionType connectionType) {

    public ResolvedHeavyModel {
        badges = badges == null ? List.of() : List.copyOf(badges);
        capability = capability == null ? ModelCapability.UNKNOWN : capability;
        executionClass = executionClass == null ? defaultExecutionClass(source) : executionClass;
    }

    public ResolvedHeavyModel(
            final String modelKey,
            final String provider,
            final String family,
            final CredentialSource source,
            final String credentialOwner,
            final String concreteModelId,
            final String displayName,
            final boolean testOnly,
            final boolean emulated,
            final String gateway,
            final String runtimeProvider,
            final String runtimeTarget,
            final List<String> badges,
            final ModelCapability capability,
            final UUID selectedConnectionId,
            final AiConnectionExecutionClass executionClass) {
        this(modelKey, provider, family, source, credentialOwner, concreteModelId, displayName, testOnly, emulated, gateway,
                runtimeProvider, runtimeTarget, badges, capability, selectedConnectionId, executionClass, null);
    }

    public ResolvedHeavyModel(
            final String modelKey,
            final String provider,
            final String family,
            final CredentialSource source,
            final String credentialOwner,
            final String concreteModelId,
            final String displayName) {
        this(modelKey, provider, family, source, credentialOwner, concreteModelId, displayName, false, false, null, null, null,
                List.of(), ModelCapability.UNKNOWN, null, null, null);
    }

    public ResolvedHeavyModel(
            final String modelKey,
            final String provider,
            final String family,
            final CredentialSource source,
            final String credentialOwner,
            final String concreteModelId,
            final String displayName,
            final boolean testOnly,
            final boolean emulated,
            final String gateway,
            final String runtimeProvider,
            final String runtimeTarget,
            final List<String> badges) {
        this(modelKey, provider, family, source, credentialOwner, concreteModelId, displayName, testOnly, emulated, gateway,
                runtimeProvider, runtimeTarget, badges, ModelCapability.UNKNOWN, null, null, null);
    }

    public ResolvedHeavyModel(
            final String modelKey,
            final String provider,
            final String family,
            final CredentialSource source,
            final String credentialOwner,
            final String concreteModelId,
            final String displayName,
            final boolean testOnly,
            final boolean emulated,
            final String gateway,
            final String runtimeProvider,
            final String runtimeTarget,
            final List<String> badges,
            final ModelCapability capability) {
        this(modelKey, provider, family, source, credentialOwner, concreteModelId, displayName, testOnly, emulated, gateway,
                runtimeProvider, runtimeTarget, badges, capability, null, null, null);
    }

    @JsonIgnore
    public boolean isLocalModel() {
        final String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        final String normalizedRuntimeProvider = runtimeProvider == null ? "" : runtimeProvider.trim().toLowerCase(Locale.ROOT);
        return "ollama".equals(normalizedProvider)
                || "openai-compatible".equals(normalizedProvider)
                || "ollama".equals(normalizedRuntimeProvider)
                || badges.stream().map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT)).anyMatch("LOCAL"::equals);
    }

    private static AiConnectionExecutionClass defaultExecutionClass(final CredentialSource source) {
        return source == CredentialSource.PLATFORM
                ? AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE
                : AiConnectionExecutionClass.USER_SINGLE_ROUTE;
    }
}
