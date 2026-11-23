package com.arslanca.orchestrai.business.delivery.manifest;

import java.util.List;

public record ManifestAgentEntry(
        String documentId,
        String role,
        String mission,
        String whenToUse,
        List<String> responsibilities,
        List<String> relatedWorkstreams,
        List<String> relatedPhaseDocumentIds,
        List<String> allowedScope,
        List<String> forbiddenScope,
        List<String> requiredInputs,
        List<String> expectedOutputs,
        List<String> qualityChecklist,
        List<String> handoffRules) {

    public ManifestAgentEntry {
        documentId = documentId == null ? "" : documentId.trim();
        role = role == null ? "" : role.trim();
        mission = mission == null ? "" : mission.trim();
        whenToUse = whenToUse == null ? "" : whenToUse.trim();
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        relatedWorkstreams = relatedWorkstreams == null ? List.of() : List.copyOf(relatedWorkstreams);
        relatedPhaseDocumentIds = relatedPhaseDocumentIds == null ? List.of() : List.copyOf(relatedPhaseDocumentIds);
        allowedScope = allowedScope == null ? List.of() : List.copyOf(allowedScope);
        forbiddenScope = forbiddenScope == null ? List.of() : List.copyOf(forbiddenScope);
        requiredInputs = requiredInputs == null ? List.of() : List.copyOf(requiredInputs);
        expectedOutputs = expectedOutputs == null ? List.of() : List.copyOf(expectedOutputs);
        qualityChecklist = qualityChecklist == null ? List.of() : List.copyOf(qualityChecklist);
        handoffRules = handoffRules == null ? List.of() : List.copyOf(handoffRules);
    }
}
