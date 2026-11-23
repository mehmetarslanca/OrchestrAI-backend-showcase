package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;

import java.util.UUID;

public record HeavyModelPreference(
        String modelKey,
        CredentialSource authSource,
        UUID selectedConnectionId) {

    public HeavyModelPreference(final String modelKey, final CredentialSource authSource) {
        this(modelKey, authSource, null);
    }

    public String defaultModelKey() {
        return modelKey;
    }

    public String defaultHeavyModelKey() {
        return modelKey;
    }

    public CredentialSource defaultAuthSource() {
        return authSource;
    }

    public UUID defaultConnectionId() {
        return selectedConnectionId;
    }
}
