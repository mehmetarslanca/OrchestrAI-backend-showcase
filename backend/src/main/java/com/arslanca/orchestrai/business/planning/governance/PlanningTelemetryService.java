package com.arslanca.orchestrai.business.planning.governance;

import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairAction;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;

public interface PlanningTelemetryService {
    void recordSnapshot(PlanningGovernanceSnapshot snapshot);

    default void recordSnapshot(final PlanningGovernanceContext context, final PlanningGovernanceSnapshot snapshot) {
        recordSnapshot(snapshot);
    }

    default void recordPreviewCacheOutcome(final PlanningGovernanceSnapshot snapshot, final String outcome) {
    }

    default void recordPreviewOutcome(final PlanningGovernanceSnapshot snapshot, final PlanningPreviewStatus status) {
    }

    default void recordPreviewAccepted(final PlanningGovernanceSnapshot snapshot) {
    }

    default void recordPreviewFinalized(
            final PlanningGovernanceSnapshot snapshot,
            final PlanningPreviewStatus status,
            final PlanningQualitySummary qualitySummary,
            final PlanningRepairOutcome repairOutcome) {
    }

    default void recordPreviewInvalidation(
            final PlanningGovernanceSnapshot snapshot,
            final PlanningPreviewStatus previousStatus,
            final String reason) {
    }

    default void recordStartReuse(final PlanningGovernanceSnapshot snapshot, final boolean acceptedPreviewReused) {
    }

    default void recordCompactRescueUsage(
            final String passName,
            final PlanningComplexity complexity,
            final PlannerModelRef plannerModelRef,
            final String rescuePath,
            final boolean succeeded) {
    }

    default void recordRepairAction(
            final PlanningGovernanceContext context,
            final PlanningBudgetDecision budgetDecision,
            final RepairAction action,
            final int attempt,
            final boolean improved) {
    }

    default void recordFallback(
            final PlanningGovernanceContext context,
            final PlanningBudgetDecision budgetDecision,
            final String reason) {
    }

    default void recordPlanningExecution(
            final PlanningGovernanceContext context,
            final PlanningBudgetDecision budgetDecision,
            final long durationMillis,
            final boolean accepted) {
    }

    default void recordPassExecution(
            final String passName,
            final PlanningComplexity complexity,
            final PlannerModelRef plannerModelRef,
            final Integer providerReportedTotalTokens,
            final int promptChars,
            final int completionChars,
            final long durationMillis) {
    }
}
