package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.util.UUID;

public final class ProviderCredential {

    private final String providerKey;
    private final String apiKey;
    private final CredentialSource source;
    private final String credentialOwner;
    private final UUID selectedConnectionId;
    private final AiConnectionType connectionType;

    public ProviderCredential(
            final String providerKey,
            final String apiKey,
            final CredentialSource source,
            final String credentialOwner) {
        this(providerKey, apiKey, source, credentialOwner, null, null);
    }

    public ProviderCredential(
            final String providerKey,
            final String apiKey,
            final CredentialSource source,
            final String credentialOwner,
            final UUID selectedConnectionId) {
        this(providerKey, apiKey, source, credentialOwner, selectedConnectionId, null);
    }

    public ProviderCredential(
            final String providerKey,
            final String apiKey,
            final CredentialSource source,
            final String credentialOwner,
            final UUID selectedConnectionId,
            final AiConnectionType connectionType) {
        this.providerKey = providerKey;
        this.apiKey = apiKey;
        this.source = source;
        this.credentialOwner = credentialOwner;
        this.selectedConnectionId = selectedConnectionId;
        this.connectionType = connectionType;
    }

    public String providerKey() {
        return providerKey;
    }

    public String apiKey() {
        return apiKey;
    }

    public CredentialSource source() {
        return source;
    }

    public String credentialOwner() {
        return credentialOwner;
    }

    public UUID selectedConnectionId() {
        return selectedConnectionId;
    }

    public AiConnectionType connectionType() {
        return connectionType;
    }

    @Override
    public String toString() {
        return "ProviderCredential[providerKey=" + providerKey
                + ", source=" + source
                + ", credentialOwner=" + credentialOwner
                + ", selectedConnectionId=" + selectedConnectionId
                + ", connectionType=" + connectionType
                + ", apiKey=[REDACTED]]";
    }
}
