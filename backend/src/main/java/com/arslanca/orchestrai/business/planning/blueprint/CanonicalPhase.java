package com.arslanca.orchestrai.business.planning.blueprint;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalPhase(
        int order,
        String title,
        String objective,
        String whyThisPhaseExists,
        List<String> scopeIn,
        List<String> scopeOut,
        List<String> prerequisites,
        List<String> deliverables,
        List<String> architectureDecisions,
        List<String> contracts,
        String dataImpact,
        List<String> securityConsiderations,
        String observabilityPlan,
        String testingStrategy,
        List<String> acceptanceCriteria,
        List<String> risks,
        String handoffInstructions) {

    public CanonicalPhase {
        title = normalizeText(title);
        objective = normalizeText(objective);
        whyThisPhaseExists = normalizeText(whyThisPhaseExists);
        scopeIn = scopeIn == null ? List.of() : List.copyOf(scopeIn);
        scopeOut = scopeOut == null ? List.of() : List.copyOf(scopeOut);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        architectureDecisions = architectureDecisions == null ? List.of() : List.copyOf(architectureDecisions);
        contracts = contracts == null ? List.of() : List.copyOf(contracts);
        dataImpact = normalizeText(dataImpact);
        securityConsiderations = securityConsiderations == null ? List.of() : List.copyOf(securityConsiderations);
        observabilityPlan = normalizeText(observabilityPlan);
        testingStrategy = normalizeText(testingStrategy);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        risks = risks == null ? List.of() : List.copyOf(risks);
        handoffInstructions = normalizeText(handoffInstructions);
    }

    private static String normalizeText(final String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
