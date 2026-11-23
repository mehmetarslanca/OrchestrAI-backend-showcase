package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.util.UUID;

public record DiscoveredHeavyModel(
        String provider,
        String family,
        String concreteModelId,
        String displayName,
        CredentialSource source,
        String credentialOwner,
        UUID selectedConnectionId,
        AiConnectionExecutionClass executionClass,
        AiConnectionType connectionType) {

    public DiscoveredHeavyModel {
        executionClass = executionClass == null ? defaultExecutionClass(source) : executionClass;
    }

    public DiscoveredHeavyModel(
            final String provider,
            final String family,
            final String concreteModelId,
            final String displayName,
            final CredentialSource source,
            final String credentialOwner,
            final UUID selectedConnectionId,
            final AiConnectionExecutionClass executionClass) {
        this(provider, family, concreteModelId, displayName, source, credentialOwner, selectedConnectionId, executionClass, null);
    }

    public DiscoveredHeavyModel(
            final String provider,
            final String family,
            final String concreteModelId,
            final String displayName,
            final CredentialSource source,
            final String credentialOwner) {
        this(provider, family, concreteModelId, displayName, source, credentialOwner, null, null, null);
    }

    public String modelKey() {
        return provider + ":" + family;
    }

    private static AiConnectionExecutionClass defaultExecutionClass(final CredentialSource source) {
        return source == CredentialSource.PLATFORM
                ? AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE
                : AiConnectionExecutionClass.USER_SINGLE_ROUTE;
    }
}
