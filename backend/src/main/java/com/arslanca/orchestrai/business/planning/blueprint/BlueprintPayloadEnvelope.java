package com.arslanca.orchestrai.business.planning.blueprint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintPayloadEnvelope(
        String payloadVersion,
        CanonicalBlueprint canonicalBlueprint) {

    public static final String CURRENT_PAYLOAD_VERSION = "1.0";

    public BlueprintPayloadEnvelope {
        payloadVersion = payloadVersion == null || payloadVersion.isBlank()
                ? CURRENT_PAYLOAD_VERSION
                : payloadVersion.trim();
    }
}
