package com.arslanca.orchestrai.business.concretes;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.BlueprintGeneratorService;
import com.arslanca.orchestrai.business.abstracts.BlueprintPlanningService;
import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.abstracts.MultiPassBlueprintPlannerService;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.dtos.orchestration.PolicyVersionFingerprint;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityGateResult;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairAction;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairStatus;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlanningResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.governance.PlanningBudgetDecision;
import com.arslanca.orchestrai.business.planning.governance.PlanningBudgetPolicy;
import com.arslanca.orchestrai.business.planning.governance.PlanningFallbackDecision;
import com.arslanca.orchestrai.business.planning.governance.PlanningFallbackPolicy;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceContext;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;

@Service
public class BlueprintPlanningManager implements BlueprintPlanningService {

    private static final Logger log = LoggerFactory.getLogger(BlueprintPlanningManager.class);

    private final BlueprintGeneratorService blueprintGeneratorService;
    private final MultiPassBlueprintPlannerService multiPassBlueprintPlannerService;
    private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;
    private final BlueprintQualityGateManager qualityGateManager;
    private final BlueprintRepairPolicy repairPolicy;
    private final PlanningBudgetPolicy planningBudgetPolicy;
    private final PlanningFallbackPolicy planningFallbackPolicy;
    private final PlanningTelemetryService planningTelemetryService;

    public BlueprintPlanningManager(
            final BlueprintGeneratorService blueprintGeneratorService,
            final MultiPassBlueprintPlannerService multiPassBlueprintPlannerService,
            final DeterministicPlanningPolicyService deterministicPlanningPolicyService,
            final BlueprintQualityGateManager qualityGateManager,
            final BlueprintRepairPolicy repairPolicy,
            final PlanningBudgetPolicy planningBudgetPolicy,
            final PlanningFallbackPolicy planningFallbackPolicy,
            final PlanningTelemetryService planningTelemetryService) {
        this.blueprintGeneratorService = blueprintGeneratorService;
        this.multiPassBlueprintPlannerService = multiPassBlueprintPlannerService;
        this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
        this.qualityGateManager = qualityGateManager;
        this.repairPolicy = repairPolicy;
        this.planningBudgetPolicy = planningBudgetPolicy;
        this.planningFallbackPolicy = planningFallbackPolicy;
        this.planningTelemetryService = planningTelemetryService;
    }

    @Override
    public BlueprintPlanningResult planBlueprint(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final PlanningModelPolicy planningModelPolicy) {
        return planBlueprint(
                analysis,
                ideTarget,
                resolvedHeavyModel,
                starterSelection,
                planningModelPolicy,
                GenerationUsageContext.empty());
    }

    @Override
    public BlueprintPlanningResult planBlueprint(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final PlanningModelPolicy planningModelPolicy,
            final GenerationUsageContext generationUsageContext) {
        final PlanningMode selectedPlanningMode = planningModelPolicy == null
                ? PlanningMode.SINGLE_PASS
                : planningModelPolicy.selectedPlanningMode();
        final PlanningGovernanceContext governanceContext = new PlanningGovernanceContext(
                null,
                analysis,
                selectedStarterKey(starterSelection),
                planningModelPolicy,
                PlanningPreview.CURRENT_CONTRACT_VERSION);
        final PlanningBudgetDecision budgetDecision = planningBudgetPolicy.decide(governanceContext);
        final long startedAt = System.nanoTime();
        final DeterministicPlanningProfile planningProfile = deterministicPlanningPolicyService.deriveProfile(analysis);

        CanonicalBlueprint currentBlueprint = generateInitialPlan(
                analysis,
                ideTarget,
                resolvedHeavyModel,
                starterSelection,
                selectedPlanningMode,
                generationUsageContext);
        QualityGateResult latestQualityResult = qualityGateManager.evaluate(currentBlueprint, analysis, planningModelPolicy, planningProfile);
        final int maxRepairAttempts = Math.min(
                repairPolicy.maxRepairAttemptsFor(planningProfile),
                budgetDecision.maxRepairAttempts());
        final List<RepairAction> repairActions = new ArrayList<>();
        int repairAttempts = 0;
        int bestScore = latestQualityResult.totalScore();
        int noImprovementCount = 0;
        String stopReason = budgetDecision.reason();

        while (!latestQualityResult.passed() && repairAttempts < maxRepairAttempts) {
            repairAttempts++;
            final RepairAction action = repairPolicy.determineRepairAction(latestQualityResult, currentBlueprint);
            if (!actionAllowed(budgetDecision, action)) {
                stopReason = switch (action) {
                    case TARGETED_EXPANSION, AGENT_REGENERATION, RULES_REPAIR -> "targeted_expansion_disallowed";
                    case FULL_REGENERATE -> "full_regenerate_disallowed";
                    case UNKNOWN -> throw new IllegalStateException("Unknown repair action cannot be budget-checked.");
                };
                planningTelemetryService.recordFallback(governanceContext, budgetDecision, stopReason);
                break;
            }
            repairActions.add(action);
            log.warn("Blueprint quality gate failed - attempt={}, verdict={}, score={}, action={}",
                    repairAttempts,
                    latestQualityResult.verdict(),
                    latestQualityResult.totalScore(),
                    action);

            currentBlueprint = executeRepairAction(
                    action,
                    analysis,
                    ideTarget,
                    resolvedHeavyModel,
                    starterSelection,
                    currentBlueprint,
                    generationUsageContext);

            latestQualityResult = qualityGateManager.evaluate(currentBlueprint, analysis, planningModelPolicy, planningProfile);

            final boolean improved = latestQualityResult.totalScore() > bestScore;
            noImprovementCount = improved ? 0 : noImprovementCount + 1;
            planningTelemetryService.recordRepairAction(
                    governanceContext,
                    budgetDecision,
                    action,
                    repairAttempts,
                    improved);
            if (improved) {
                bestScore = latestQualityResult.totalScore();
            }

            final PlanningFallbackDecision fallbackDecision = planningFallbackPolicy.evaluateAfterRepair(
                    budgetDecision,
                    noImprovementCount);
            if (!fallbackDecision.continueRepairs()) {
                stopReason = fallbackDecision.reason();
                log.warn(
                        "Blueprint planning stopped early by governance fallback - attempt={}, reason={}, score={}",
                        repairAttempts,
                        stopReason,
                        latestQualityResult.totalScore());
                planningTelemetryService.recordFallback(governanceContext, budgetDecision, stopReason);
                break;
            }
        }

        final boolean accepted = latestQualityResult.passed();
        planningTelemetryService.recordPlanningExecution(
                governanceContext,
                budgetDecision,
                (System.nanoTime() - startedAt) / 1_000_000L,
                accepted);
        return new BlueprintPlanningResult(
                currentBlueprint,
                accepted,
                buildQualitySummary(analysis, planningProfile, planningModelPolicy, starterSelection, latestQualityResult),
                buildRepairOutcome(latestQualityResult, repairAttempts, maxRepairAttempts, repairActions, accepted, stopReason));
    }

    private CanonicalBlueprint generateInitialPlan(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final PlanningMode selectedPlanningMode,
            final GenerationUsageContext generationUsageContext) {
        if (selectedPlanningMode == PlanningMode.SINGLE_PASS) {
            return blueprintGeneratorService.generateBlueprint(
                    analysis,
                    ideTarget,
                    resolvedHeavyModel,
                    starterSelection,
                    generationUsageContext);
        }
        return multiPassBlueprintPlannerService.generateBlueprint(
                analysis,
                ideTarget,
                resolvedHeavyModel,
                starterSelection,
                selectedPlanningMode == PlanningMode.REDUCED_MULTI_PASS,
                generationUsageContext);
    }

    private PlanningQualitySummary buildQualitySummary(
            final StructuredAnalysis analysis,
            final DeterministicPlanningProfile planningProfile,
            final PlanningModelPolicy planningModelPolicy,
            final StarterSelection starterSelection,
            final QualityGateResult qualityGateResult) {
        final CapabilityRisk capabilityRisk = planningModelPolicy == null ? CapabilityRisk.UNKNOWN : planningModelPolicy.capabilityRisk();
        final PlannerModelRef plannerModelRef = planningModelPolicy == null ? PlannerModelRef.unknown() : planningModelPolicy.plannerModelRef();
        final DegradeStrategy degradeStrategy = planningModelPolicy == null ? DegradeStrategy.NONE : planningModelPolicy.degradeStrategy();
        final PolicyVersionFingerprint policyVersionFingerprint = planningModelPolicy == null
                ? PolicyVersionFingerprint.unknown()
                : planningModelPolicy.policyVersionFingerprint();

        return new PlanningQualitySummary(
                analysis.planningComplexity(),
                analysis.complexityMarkers(),
                capabilityRisk,
                planningModelPolicy != null && planningModelPolicy.previewRequired(),
                planningModelPolicy != null && planningModelPolicy.previewRecommended(),
                plannerModelRef,
                degradeStrategy,
                starterSelection.docsOnlyFallback(),
                qualityGateResult.totalScore(),
                qualityGateResult.passingScore(),
                qualityGateResult.verdict(),
                qualityGateResult.passed(),
                PlanningDecisionPayloadMapper.fromLegacyCategoryScores(qualityGateResult.categoryScores()),
                planningProfile == null ? analysis.planningComplexity() : planningProfile.effectiveComplexity(),
                qualityGateResult.shapeAssessment(),
                qualityGateResult.deterministicFindings(),
                qualityGateResult.findings(),
                policyVersionFingerprint);
    }

    private CanonicalBlueprint executeRepairAction(
            final RepairAction action,
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final CanonicalBlueprint currentBlueprint,
            final GenerationUsageContext generationUsageContext) {
        return switch (action) {
            case TARGETED_EXPANSION, AGENT_REGENERATION, RULES_REPAIR -> multiPassBlueprintPlannerService.expandBlueprintDepth(
                    analysis,
                    ideTarget,
                    resolvedHeavyModel,
                    starterSelection,
                    currentBlueprint,
                    generationUsageContext);
            case FULL_REGENERATE -> multiPassBlueprintPlannerService.generateBlueprint(
                    analysis,
                    ideTarget,
                    resolvedHeavyModel,
                    starterSelection,
                    false,
                    generationUsageContext);
            case UNKNOWN -> throw new IllegalStateException("Unknown repair action cannot be executed.");
        };
    }

    private PlanningRepairOutcome buildRepairOutcome(
            final QualityGateResult qualityGateResult,
            final int repairAttempts,
            final int maxRepairAttempts,
            final List<RepairAction> repairActions,
            final boolean accepted,
            final String reason) {
        return new PlanningRepairOutcome(
                accepted
                        ? (repairAttempts == 0 ? RepairStatus.NOT_REQUIRED : RepairStatus.COMPLETED)
                        : RepairStatus.REJECTED,
                repairAttempts,
                maxRepairAttempts,
                repairActions,
                qualityGateResult.verdict(),
                qualityGateResult.totalScore(),
                qualityGateResult.passingScore(),
                qualityGateResult.findings(),
                accepted ? "" : (reason == null ? "" : reason));
    }

    private boolean actionAllowed(final PlanningBudgetDecision budgetDecision, final RepairAction action) {
        if (budgetDecision == null) {
            return true;
        }
        return switch (action) {
            case TARGETED_EXPANSION, AGENT_REGENERATION, RULES_REPAIR -> budgetDecision.allowTargetedExpansion();
            case FULL_REGENERATE -> budgetDecision.allowFullRegenerate();
            case UNKNOWN -> throw new IllegalStateException("Unknown repair action cannot be evaluated.");
        };
    }

    private String selectedStarterKey(final StarterSelection starterSelection) {
        if (starterSelection == null || starterSelection.docsOnlyFallback() || starterSelection.recommendedStarter() == null) {
            return "docs_only";
        }
        return starterSelection.recommendedStarter().key();
    }
}
