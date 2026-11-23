package com.arslanca.orchestrai.business.delivery.manifest;

import java.util.List;

public record ManifestPhaseEntry(
        String documentId,
        int order,
        String title,
        String purpose,
        String whyThisPhaseExists,
        List<String> dependencies,
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

    public ManifestPhaseEntry {
        documentId = documentId == null ? "" : documentId.trim();
        title = title == null ? "" : title.trim();
        purpose = purpose == null ? "" : purpose.trim();
        whyThisPhaseExists = whyThisPhaseExists == null ? "" : whyThisPhaseExists.trim();
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        architectureDecisions = architectureDecisions == null ? List.of() : List.copyOf(architectureDecisions);
        contracts = contracts == null ? List.of() : List.copyOf(contracts);
        dataImpact = dataImpact == null ? "" : dataImpact.trim();
        securityConsiderations = securityConsiderations == null ? List.of() : List.copyOf(securityConsiderations);
        observabilityPlan = observabilityPlan == null ? "" : observabilityPlan.trim();
        testingStrategy = testingStrategy == null ? "" : testingStrategy.trim();
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        risks = risks == null ? List.of() : List.copyOf(risks);
        handoffInstructions = handoffInstructions == null ? "" : handoffInstructions.trim();
    }
}
