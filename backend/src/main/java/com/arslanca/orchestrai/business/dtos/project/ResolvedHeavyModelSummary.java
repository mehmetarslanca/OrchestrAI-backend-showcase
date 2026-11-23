package com.arslanca.orchestrai.business.dtos.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResolvedHeavyModelSummary(
        String modelKey,
        String provider,
        String family,
        String source,
        String authSource,
        String credentialOwner,
        String concreteModelId,
        String displayName,
        boolean testOnly,
        boolean emulated,
        String gateway,
        String runtimeProvider,
        String runtimeTarget,
        List<String> badges) {

    public ResolvedHeavyModelSummary {
        badges = badges == null ? List.of() : List.copyOf(badges);
    }
}
