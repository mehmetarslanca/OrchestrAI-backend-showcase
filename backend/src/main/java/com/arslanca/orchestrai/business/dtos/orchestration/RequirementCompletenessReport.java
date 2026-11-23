package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RequirementCompletenessReport(
        boolean ready,
        List<String> missingRequiredSignals,
        List<String> unresolvedRequiredSignals,
        List<String> explicitUnknownSignals,
        String contractVersion) {

    public static final String CURRENT_CONTRACT_VERSION = "1.0";

    public RequirementCompletenessReport {
        missingRequiredSignals = missingRequiredSignals == null ? List.of() : List.copyOf(missingRequiredSignals);
        unresolvedRequiredSignals = unresolvedRequiredSignals == null ? List.of() : List.copyOf(unresolvedRequiredSignals);
        explicitUnknownSignals = explicitUnknownSignals == null ? List.of() : List.copyOf(explicitUnknownSignals);
        contractVersion = contractVersion == null || contractVersion.isBlank() ? CURRENT_CONTRACT_VERSION : contractVersion.trim();
    }
}
