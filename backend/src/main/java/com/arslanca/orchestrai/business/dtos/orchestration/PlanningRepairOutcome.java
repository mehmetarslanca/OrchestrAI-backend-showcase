package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

public record PlanningRepairOutcome(
        RepairStatus status,
        int repairAttempts,
        int maxRepairAttempts,
        List<RepairAction> actions,
        QualityVerdict finalVerdict,
        int score,
        int passingScore,
        List<String> findings,
        String reason) {

    public PlanningRepairOutcome {
        status = status == null ? RepairStatus.UNKNOWN : status;
        actions = actions == null ? List.of() : List.copyOf(actions);
        finalVerdict = finalVerdict == null ? QualityVerdict.REJECT : finalVerdict;
        findings = findings == null ? List.of() : List.copyOf(findings);
        reason = reason == null ? "" : reason.trim();
    }

    public PlanningRepairOutcome withStatus(final RepairStatus updatedStatus, final String updatedReason) {
        return new PlanningRepairOutcome(
                updatedStatus,
                repairAttempts,
                maxRepairAttempts,
                actions,
                finalVerdict,
                score,
                passingScore,
                findings,
                updatedReason);
    }
}
