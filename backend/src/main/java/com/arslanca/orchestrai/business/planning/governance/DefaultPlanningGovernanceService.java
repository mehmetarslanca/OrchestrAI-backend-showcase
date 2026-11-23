package com.arslanca.orchestrai.business.planning.governance;

import com.arslanca.orchestrai.business.dtos.orchestration.PolicyVersionFingerprint;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlanningGovernanceService implements PlanningGovernanceService {

    private final PlanningBudgetPolicy planningBudgetPolicy;
    private final PreviewBillingPolicy previewBillingPolicy;
    private final PreviewCachePolicy previewCachePolicy;
    private final PlanningTelemetryService planningTelemetryService;
    private final PlanningRolloutPolicy planningRolloutPolicy;
    private final PolicyVersionFingerprintResolver fingerprintResolver;

    public DefaultPlanningGovernanceService(
            final PlanningBudgetPolicy planningBudgetPolicy,
            final PreviewBillingPolicy previewBillingPolicy,
            final PreviewCachePolicy previewCachePolicy,
            final PlanningTelemetryService planningTelemetryService,
            final PlanningRolloutPolicy planningRolloutPolicy,
            final PolicyVersionFingerprintResolver fingerprintResolver) {
        this.planningBudgetPolicy = planningBudgetPolicy;
        this.previewBillingPolicy = previewBillingPolicy;
        this.previewCachePolicy = previewCachePolicy;
        this.planningTelemetryService = planningTelemetryService;
        this.planningRolloutPolicy = planningRolloutPolicy;
        this.fingerprintResolver = fingerprintResolver;
    }

    @Override
    public PlanningGovernanceSnapshot createSnapshot(final PlanningGovernanceContext context) {
        final PlanningBudgetDecision budgetDecision = planningBudgetPolicy.decide(context);
        final PreviewBillingDecision billingDecision = previewBillingPolicy.resolveDecision(context);
        final PreviewCacheDecision cacheDecision = previewCachePolicy.buildDecision(context);
        final PlanningRolloutDecision rolloutDecision = planningRolloutPolicy.resolveDecision(context);
        final PolicyVersionFingerprint governanceFingerprint = fingerprintResolver.resolve(context);
        final PlanningComplexity complexity = context == null || context.structuredAnalysis() == null
                ? PlanningComplexity.MODERATE
                : context.structuredAnalysis().planningComplexity();
        final PlanningComplexity effectiveComplexity = context == null
                ? PlanningComplexity.MODERATE
                : context.effectiveComplexity();
        final PlanningMode planningMode = context == null || context.planningModelPolicy() == null
                ? PlanningMode.SINGLE_PASS
                : context.planningModelPolicy().selectedPlanningMode();
        final PlannerModelRef plannerModelRef = context == null || context.planningModelPolicy() == null
                ? PlannerModelRef.unknown()
                : context.planningModelPolicy().plannerModelRef();
        final PolicyVersionFingerprint planningFingerprint = context == null || context.planningModelPolicy() == null
                ? PolicyVersionFingerprint.unknown()
                : context.planningModelPolicy().policyVersionFingerprint();
        final DegradeStrategy degradeStrategy = context == null || context.planningModelPolicy() == null
                ? DegradeStrategy.NONE
                : context.planningModelPolicy().degradeStrategy();
        final PlanningGovernanceSnapshot snapshot = new PlanningGovernanceSnapshot(
                PlanningGovernanceSnapshot.CURRENT_SCHEMA_VERSION,
                complexity,
                effectiveComplexity,
                context == null ? null : context.shapeEnvelope(),
                planningMode,
                plannerModelRef,
                planningFingerprint,
                governanceFingerprint,
                context == null ? null : context.selectedStarterKey(),
                context == null ? null : context.resolvedIdeTarget(),
                degradeStrategy,
                context == null ? null : context.preparationContractVersion(),
                context == null ? null : context.previewContractVersion(),
                budgetDecision,
                billingDecision,
                cacheDecision,
                rolloutDecision);
        planningTelemetryService.recordSnapshot(context, snapshot);
        return snapshot;
    }
}
