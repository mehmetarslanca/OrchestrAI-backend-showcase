package com.arslanca.orchestrai.business.delivery.composition;

import java.util.List;

public record AgentDeliveryContext(
        String mission,
        String whenToUse,
        List<String> allowedScope,
        List<String> forbiddenScope,
        List<String> requiredInputs,
        List<String> expectedOutputs,
        List<String> qualityChecklist,
        List<String> handoffRules,
        List<String> relatedWorkstreams,
        List<String> relatedPhaseDocumentIds) {

    public AgentDeliveryContext {
        mission = mission == null ? "" : mission.trim();
        whenToUse = whenToUse == null ? "" : whenToUse.trim();
        allowedScope = allowedScope == null ? List.of() : List.copyOf(allowedScope);
        forbiddenScope = forbiddenScope == null ? List.of() : List.copyOf(forbiddenScope);
        requiredInputs = requiredInputs == null ? List.of() : List.copyOf(requiredInputs);
        expectedOutputs = expectedOutputs == null ? List.of() : List.copyOf(expectedOutputs);
        qualityChecklist = qualityChecklist == null ? List.of() : List.copyOf(qualityChecklist);
        handoffRules = handoffRules == null ? List.of() : List.copyOf(handoffRules);
        relatedWorkstreams = relatedWorkstreams == null ? List.of() : List.copyOf(relatedWorkstreams);
        relatedPhaseDocumentIds = relatedPhaseDocumentIds == null ? List.of() : List.copyOf(relatedPhaseDocumentIds);
    }
}
