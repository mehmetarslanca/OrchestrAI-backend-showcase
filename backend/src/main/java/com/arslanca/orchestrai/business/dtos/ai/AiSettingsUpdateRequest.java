package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AiSettingsUpdateRequest(
        @Size(max = 120, message = "Default heavy model anahtari en fazla 120 karakter olabilir")
        String defaultHeavyModelKey,
        CredentialSource defaultAuthSource,
        @Size(max = 120, message = "Default model anahtari en fazla 120 karakter olabilir")
        String defaultModelKey,
        UUID defaultConnectionId) {

    public AiSettingsUpdateRequest(final String defaultHeavyModelKey, final CredentialSource defaultAuthSource) {
        this(defaultHeavyModelKey, defaultAuthSource, null, null);
    }

    public AiSettingsUpdateRequest(
            final String defaultHeavyModelKey,
            final CredentialSource defaultAuthSource,
            final String defaultModelKey,
            final UUID defaultConnectionId) {
        this.defaultHeavyModelKey = defaultHeavyModelKey;
        this.defaultAuthSource = defaultAuthSource;
        this.defaultModelKey = defaultModelKey;
        this.defaultConnectionId = defaultConnectionId;
    }
}
