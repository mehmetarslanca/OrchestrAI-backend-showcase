package com.arslanca.orchestrai.business.planning.governance;

import java.util.concurrent.TimeUnit;

import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingCode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeAssessment;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairAction;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class MicrometerPlanningTelemetryService implements PlanningTelemetryService {

    private final MeterRegistry meterRegistry;

    public MicrometerPlanningTelemetryService(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordSnapshot(final PlanningGovernanceSnapshot snapshot) {
        recordSnapshot(null, snapshot);
    }

    @Override
    public void recordSnapshot(
            final PlanningGovernanceContext context,
            final PlanningGovernanceSnapshot snapshot) {
        meterRegistry.counter(
                "planning.governance.snapshot",
                "complexity", complexityTag(snapshot).name().toLowerCase(),
                "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                "planningMode", planningModeTag(snapshot),
                "envelope", envelopeTag(snapshot),
                "rolloutMode", rolloutModeTag(snapshot),
                "rolloutReason", rolloutReasonTag(snapshot),
                "localModel", localModelTag(snapshot),
                "uplifted", String.valueOf(isComplexityUplifted(snapshot)),
                "upliftReason", upliftReasonTag(context, snapshot)
        ).increment();
    }

    @Override
    public void recordPreviewCacheOutcome(final PlanningGovernanceSnapshot snapshot, final String outcome) {
        meterRegistry.counter(
                "planning.preview.cache",
                "complexity", complexityTag(snapshot).name().toLowerCase(),
                "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                "planningMode", planningModeTag(snapshot),
                "rolloutMode", rolloutModeTag(snapshot),
                "outcome", tagValue(outcome),
                "localModel", localModelTag(snapshot))
                .increment();
    }

    @Override
    public void recordPreviewOutcome(final PlanningGovernanceSnapshot snapshot, final PlanningPreviewStatus status) {
        meterRegistry.counter(
                "planning.preview.outcome",
                "complexity", complexityTag(snapshot).name().toLowerCase(),
                "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                "planningMode", planningModeTag(snapshot),
                "rolloutMode", rolloutModeTag(snapshot),
                "status", tagValue(status.name()),
                "localModel", localModelTag(snapshot))
                .increment();
    }

    @Override
    public void recordPreviewAccepted(final PlanningGovernanceSnapshot snapshot) {
        meterRegistry.counter(
                "planning.preview.acceptance",
                "complexity", complexityTag(snapshot).name().toLowerCase(),
                "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                "planningMode", planningModeTag(snapshot),
                "rolloutMode", rolloutModeTag(snapshot),
                "localModel", localModelTag(snapshot))
                .increment();
    }

    @Override
    public void recordPreviewFinalized(
            final PlanningGovernanceSnapshot snapshot,
            final PlanningPreviewStatus status,
            final PlanningQualitySummary qualitySummary,
            final PlanningRepairOutcome repairOutcome) {
        meterRegistry.counter(
                "planning.preview.final_quality",
                "complexity", complexityTag(snapshot).name().toLowerCase(),
                "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                "planningMode", planningModeTag(snapshot),
                "rolloutMode", rolloutModeTag(snapshot),
                "previewStatus", tagValue(status == null ? null : status.name()),
                "verdict", tagValue(qualitySummary == null ? null : qualitySummary.qualityVerdict().value()),
                "repairStatus", tagValue(repairOutcome == null ? null : repairOutcome.status().value()),
                "repairAction", finalRepairActionTag(repairOutcome),
                "reasonSource", finalReasonSourceTag(qualitySummary, repairOutcome),
                "reasonCode", finalReasonCodeTag(qualitySummary, repairOutcome))
                .increment();

        recordShapeMetric(snapshot, qualitySummary, repairOutcome, status, "workstreams",
                shapeAssessment(qualitySummary).expectedShape().minWorkstreamCount(),
                shapeAssessment(qualitySummary).expectedShape().maxWorkstreamCount(),
                shapeAssessment(qualitySummary).actualShape().workstreamCount());
        recordShapeMetric(snapshot, qualitySummary, repairOutcome, status, "phases",
                shapeAssessment(qualitySummary).expectedShape().minPhaseCount(),
                shapeAssessment(qualitySummary).expectedShape().maxPhaseCount(),
                shapeAssessment(qualitySummary).actualShape().phaseCount());
        recordShapeMetric(snapshot, qualitySummary, repairOutcome, status, "agents",
                shapeAssessment(qualitySummary).expectedShape().minAgentCount(),
                shapeAssessment(qualitySummary).expectedShape().maxAgentCount(),
                shapeAssessment(qualitySummary).actualShape().agentCount());
    }

    @Override
    public void recordPreviewInvalidation(
            final PlanningGovernanceSnapshot snapshot,
            final PlanningPreviewStatus previousStatus,
            final String reason) {
        meterRegistry.counter(
                "planning.preview.invalidation",
                "complexity", complexityTag(snapshot).name().toLowerCase(),
                "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                "planningMode", planningModeTag(snapshot),
                "rolloutMode", rolloutModeTag(snapshot),
                "previousStatus", tagValue(previousStatus == null ? null : previousStatus.name()),
                "reason", tagValue(reason))
                .increment();
    }

    @Override
    public void recordStartReuse(final PlanningGovernanceSnapshot snapshot, final boolean acceptedPreviewReused) {
        meterRegistry.counter(
                "planning.start.reuse",
                "complexity", complexityTag(snapshot).name().toLowerCase(),
                "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                "planningMode", planningModeTag(snapshot),
                "rolloutMode", rolloutModeTag(snapshot),
                "mode", acceptedPreviewReused ? "accepted_preview" : "fresh_planning",
                "billingDisposition", tagValue(acceptedPreviewReused
                        ? snapshot.billingDecision().acceptedPreviewStartDisposition()
                        : snapshot.billingDecision().freshStartDisposition()))
                .increment();
    }

    @Override
    public void recordCompactRescueUsage(
            final String passName,
            final PlanningComplexity complexity,
            final PlannerModelRef plannerModelRef,
            final String rescuePath,
            final boolean succeeded) {
        meterRegistry.counter(
                "planning.pass.compact_rescue",
                "passName", tagValue(passName),
                "complexity", (complexity == null ? PlanningComplexity.MODERATE : complexity).name().toLowerCase(),
                "provider", providerTag(plannerModelRef),
                "localModel", String.valueOf(plannerModelRef != null && plannerModelRef.localModel()),
                "rescuePath", tagValue(rescuePath),
                "outcome", succeeded ? "success" : "failed")
                .increment();
    }

    @Override
    public void recordRepairAction(
            final PlanningGovernanceContext context,
            final PlanningBudgetDecision budgetDecision,
            final RepairAction action,
            final int attempt,
            final boolean improved) {
        meterRegistry.counter(
                "planning.repair.loop",
                "complexity", complexityTag(context).name().toLowerCase(),
                "envelope", tagValue(budgetDecision == null ? null : budgetDecision.envelopeCode()),
                "action", tagValue(action == null ? null : action.value()),
                "attempt", String.valueOf(Math.max(0, attempt)),
                "improved", String.valueOf(improved))
                .increment();
    }

    @Override
    public void recordFallback(
            final PlanningGovernanceContext context,
            final PlanningBudgetDecision budgetDecision,
            final String reason) {
        meterRegistry.counter(
                "planning.fallback",
                "complexity", complexityTag(context).name().toLowerCase(),
                "envelope", tagValue(budgetDecision == null ? null : budgetDecision.envelopeCode()),
                "reason", tagValue(reason))
                .increment();
    }

    @Override
    public void recordPlanningExecution(
            final PlanningGovernanceContext context,
            final PlanningBudgetDecision budgetDecision,
            final long durationMillis,
            final boolean accepted) {
        meterRegistry.timer(
                        "planning.execution.duration",
                        "complexity", complexityTag(context).name().toLowerCase(),
                        "envelope", tagValue(budgetDecision == null ? null : budgetDecision.envelopeCode()),
                        "accepted", String.valueOf(accepted))
                .record(Math.max(0L, durationMillis), TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordPassExecution(
            final String passName,
            final PlanningComplexity complexity,
            final PlannerModelRef plannerModelRef,
            final Integer providerReportedTotalTokens,
            final int promptChars,
            final int completionChars,
            final long durationMillis) {
        final Integer totalTokens = providerReportedTotalTokens != null && providerReportedTotalTokens > 0
                ? providerReportedTotalTokens
                : estimateTokens(promptChars) + estimateTokens(completionChars);
        final String usageSource = providerReportedTotalTokens != null && providerReportedTotalTokens > 0
                ? "provider"
                : "estimated";

        meterRegistry.timer(
                        "planning.pass.duration",
                        "passName", tagValue(passName),
                        "complexity", (complexity == null ? PlanningComplexity.MODERATE : complexity).name().toLowerCase(),
                        "provider", providerTag(plannerModelRef),
                        "localModel", String.valueOf(plannerModelRef != null && plannerModelRef.localModel()))
                .record(Math.max(0L, durationMillis), TimeUnit.MILLISECONDS);
        meterRegistry.summary(
                        "planning.usage.tokens",
                        "passName", tagValue(passName),
                        "complexity", (complexity == null ? PlanningComplexity.MODERATE : complexity).name().toLowerCase(),
                        "provider", providerTag(plannerModelRef),
                        "localModel", String.valueOf(plannerModelRef != null && plannerModelRef.localModel()),
                        "usageSource", usageSource)
                .record(Math.max(0, totalTokens));
    }

    private PlanningComplexity complexityTag(final PlanningGovernanceContext context) {
        return context == null || context.structuredAnalysis() == null
                ? PlanningComplexity.MODERATE
                : context.structuredAnalysis().planningComplexity();
    }

    private PlanningComplexity complexityTag(final PlanningGovernanceSnapshot snapshot) {
        return snapshot == null || snapshot.complexity() == null
                ? PlanningComplexity.MODERATE
                : snapshot.complexity();
    }

    private PlanningComplexity effectiveComplexityTag(final PlanningGovernanceSnapshot snapshot) {
        return snapshot == null || snapshot.effectiveComplexity() == null
                ? complexityTag(snapshot)
                : snapshot.effectiveComplexity();
    }

    private String planningModeTag(final PlanningGovernanceSnapshot snapshot) {
        return tagValue(snapshot == null || snapshot.planningMode() == null ? null : snapshot.planningMode().name());
    }

    private String envelopeTag(final PlanningGovernanceSnapshot snapshot) {
        return tagValue(snapshot == null || snapshot.budgetDecision() == null ? null : snapshot.budgetDecision().envelopeCode());
    }

    private String rolloutModeTag(final PlanningGovernanceSnapshot snapshot) {
        return tagValue(snapshot == null || snapshot.rolloutDecision() == null ? null : snapshot.rolloutDecision().mode().value());
    }

    private String rolloutReasonTag(final PlanningGovernanceSnapshot snapshot) {
        return tagValue(snapshot == null || snapshot.rolloutDecision() == null ? null : snapshot.rolloutDecision().reason());
    }

    private String localModelTag(final PlanningGovernanceSnapshot snapshot) {
        return String.valueOf(snapshot != null && snapshot.plannerModelRef() != null && snapshot.plannerModelRef().localModel());
    }

    private boolean isComplexityUplifted(final PlanningGovernanceSnapshot snapshot) {
        return effectiveComplexityTag(snapshot).ordinal() > complexityTag(snapshot).ordinal();
    }

    private String upliftReasonTag(
            final PlanningGovernanceContext context,
            final PlanningGovernanceSnapshot snapshot) {
        if (!isComplexityUplifted(snapshot)) {
            return "none";
        }
        if (context == null || context.planningProfile() == null || context.planningProfile().hardSignals().isEmpty()) {
            return "unknown";
        }
        return context.planningProfile().hardSignals().stream()
                .filter(signal -> signal != null && signal != PlanningHardSignal.UNKNOWN)
                .findFirst()
                .map(PlanningHardSignal::value)
                .map(this::tagValue)
                .orElse("unknown");
    }

    private PlanningShapeAssessment shapeAssessment(final PlanningQualitySummary qualitySummary) {
        return qualitySummary == null ? PlanningShapeAssessment.unknown() : qualitySummary.shapeAssessment();
    }

    private String finalRepairActionTag(final PlanningRepairOutcome repairOutcome) {
        if (repairOutcome == null || repairOutcome.actions().isEmpty()) {
            return "none";
        }
        return tagValue(repairOutcome.actions().get(repairOutcome.actions().size() - 1).value());
    }

    private String finalReasonSourceTag(
            final PlanningQualitySummary qualitySummary,
            final PlanningRepairOutcome repairOutcome) {
        final PlanningQualityFindingCode findingCode = primaryFindingCode(qualitySummary);
        if (findingCode != PlanningQualityFindingCode.UNKNOWN) {
            return "quality_finding";
        }
        if (repairOutcome != null && repairOutcome.reason() != null && !repairOutcome.reason().isBlank()) {
            return "repair_outcome";
        }
        return "none";
    }

    private String finalReasonCodeTag(
            final PlanningQualitySummary qualitySummary,
            final PlanningRepairOutcome repairOutcome) {
        final PlanningQualityFindingCode findingCode = primaryFindingCode(qualitySummary);
        if (findingCode != PlanningQualityFindingCode.UNKNOWN) {
            return tagValue(findingCode.value());
        }
        if (repairOutcome != null && repairOutcome.reason() != null && !repairOutcome.reason().isBlank()) {
            return tagValue(repairOutcome.reason());
        }
        return "none";
    }

    private PlanningQualityFindingCode primaryFindingCode(final PlanningQualitySummary qualitySummary) {
        if (qualitySummary == null || qualitySummary.deterministicFindings().isEmpty()) {
            return PlanningQualityFindingCode.UNKNOWN;
        }
        return qualitySummary.deterministicFindings().getFirst().code();
    }

    private void recordShapeMetric(
            final PlanningGovernanceSnapshot snapshot,
            final PlanningQualitySummary qualitySummary,
            final PlanningRepairOutcome repairOutcome,
            final PlanningPreviewStatus status,
            final String dimension,
            final int expectedMin,
            final int expectedMax,
            final int actual) {
        meterRegistry.summary(
                        "planning.preview.shape.expected",
                        "dimension", dimension,
                        "bound", "min",
                        "complexity", complexityTag(snapshot).name().toLowerCase(),
                        "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                        "rolloutMode", rolloutModeTag(snapshot),
                        "verdict", tagValue(qualitySummary == null ? null : qualitySummary.qualityVerdict().value()),
                        "repairAction", finalRepairActionTag(repairOutcome),
                        "previewStatus", tagValue(status == null ? null : status.name()))
                .record(Math.max(0, expectedMin));
        meterRegistry.summary(
                        "planning.preview.shape.expected",
                        "dimension", dimension,
                        "bound", "max",
                        "complexity", complexityTag(snapshot).name().toLowerCase(),
                        "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                        "rolloutMode", rolloutModeTag(snapshot),
                        "verdict", tagValue(qualitySummary == null ? null : qualitySummary.qualityVerdict().value()),
                        "repairAction", finalRepairActionTag(repairOutcome),
                        "previewStatus", tagValue(status == null ? null : status.name()))
                .record(Math.max(0, expectedMax));
        meterRegistry.summary(
                        "planning.preview.shape.actual",
                        "dimension", dimension,
                        "complexity", complexityTag(snapshot).name().toLowerCase(),
                        "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                        "rolloutMode", rolloutModeTag(snapshot),
                        "verdict", tagValue(qualitySummary == null ? null : qualitySummary.qualityVerdict().value()),
                        "repairAction", finalRepairActionTag(repairOutcome),
                        "previewStatus", tagValue(status == null ? null : status.name()))
                .record(Math.max(0, actual));
        meterRegistry.summary(
                        "planning.preview.shape.ratio",
                        "dimension", dimension,
                        "reference", "min",
                        "complexity", complexityTag(snapshot).name().toLowerCase(),
                        "effectiveComplexity", effectiveComplexityTag(snapshot).name().toLowerCase(),
                        "rolloutMode", rolloutModeTag(snapshot),
                        "verdict", tagValue(qualitySummary == null ? null : qualitySummary.qualityVerdict().value()),
                        "repairAction", finalRepairActionTag(repairOutcome),
                        "previewStatus", tagValue(status == null ? null : status.name()))
                .record(ratio(actual, expectedMin));
    }

    private double ratio(final int actual, final int expectedFloor) {
        if (expectedFloor <= 0) {
            return actual <= 0 ? 1.0d : (double) actual;
        }
        return Math.max(0d, (double) Math.max(0, actual) / (double) expectedFloor);
    }

    private String providerTag(final PlannerModelRef plannerModelRef) {
        if (plannerModelRef == null || plannerModelRef.resolvedModelKey().isBlank()) {
            return "unknown";
        }
        final String modelKey = plannerModelRef.resolvedModelKey();
        final int separatorIndex = modelKey.indexOf(':');
        return separatorIndex > 0 ? tagValue(modelKey.substring(0, separatorIndex)) : tagValue(modelKey);
    }

    private int estimateTokens(final int charCount) {
        return Math.max(1, (Math.max(0, charCount) + 3) / 4);
    }

    private String tagValue(final String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }
}
