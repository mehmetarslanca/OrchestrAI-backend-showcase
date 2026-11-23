package com.arslanca.orchestrai.business.planning.governance;

public record PlanningBudgetDecision(
        boolean allowed,
        String envelopeCode,
        int maxRepairAttempts,
        int maxNoImprovementAttempts,
        boolean allowTargetedExpansion,
        boolean allowFullRegenerate,
        String reason) {

    public PlanningBudgetDecision {
        envelopeCode = envelopeCode == null || envelopeCode.isBlank() ? "moderate_partial" : envelopeCode.trim();
        maxRepairAttempts = Math.max(0, maxRepairAttempts);
        maxNoImprovementAttempts = Math.max(0, maxNoImprovementAttempts);
        reason = reason == null ? "" : reason.trim();
    }
}
