package com.arslanca.orchestrai.business.planning.preparation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;

@Component
public class PlanningSummaryFactory {

    private static final Logger log = LoggerFactory.getLogger(PlanningSummaryFactory.class);

    private final HeavyModelResolutionService heavyModelResolutionService;
    private final PlanningModelPolicyService planningModelPolicyService;
    private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;

    public PlanningSummaryFactory(
            final HeavyModelResolutionService heavyModelResolutionService,
            final PlanningModelPolicyService planningModelPolicyService,
            final DeterministicPlanningPolicyService deterministicPlanningPolicyService) {
        this.heavyModelResolutionService = heavyModelResolutionService;
        this.planningModelPolicyService = planningModelPolicyService;
        this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
    }

    public PlanningSummary buildPlanningSummary(
            final UUID userId,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection) {
        final DeterministicPlanningProfile profile = analysis == null
                ? DeterministicPlanningProfile.unknown()
                : deterministicPlanningPolicyService.deriveProfile(analysis);
        final PlanningComplexity complexity = profile.effectiveComplexity();

        final PlanningModelPolicy planningModelPolicy = resolvePlanningModelPolicy(userId, complexity, starterSelection.docsOnlyFallback());

        return buildPlanningSummary(analysis, starterSelection, planningModelPolicy, profile);
    }

    public PlanningSummary buildPlanningSummary(
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final ResolvedHeavyModel resolvedHeavyModel) {
        final DeterministicPlanningProfile profile = analysis == null
                ? DeterministicPlanningProfile.unknown()
                : deterministicPlanningPolicyService.deriveProfile(analysis);
        final PlanningComplexity complexity = profile.effectiveComplexity();

        final PlanningModelPolicy planningModelPolicy = planningModelPolicyService.resolvePolicy(
                complexity,
                resolvedHeavyModel,
                starterSelection.docsOnlyFallback());

        return buildPlanningSummary(analysis, starterSelection, planningModelPolicy, profile);
    }

    private PlanningSummary buildPlanningSummary(
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final PlanningModelPolicy planningModelPolicy,
            final DeterministicPlanningProfile profile) {
        final PlanningComplexity rawComplexity = analysis == null ? PlanningComplexity.MODERATE : analysis.planningComplexity();
        final DeterministicPlanningProfile normalizedProfile = profile == null ? DeterministicPlanningProfile.unknown() : profile;

        return new PlanningSummary(
                rawComplexity,
                analysis == null ? List.of() : analysis.complexityMarkers(),
                buildProjectShapeSummary(analysis),
                planningModelPolicy.selectedPlanningMode(),
                planningModelPolicy.capabilityRisk(),
                planningModelPolicy.previewRecommended(),
                planningModelPolicy.previewRequired(),
                starterSelection.docsOnlyFallback(),
                buildStarterSummary(starterSelection),
                planningModelPolicy.resolvedModelKey(),
                planningModelPolicy.modelCapability(),
                planningModelPolicy.localModel(),
                planningModelPolicy.degradeStrategy(),
                PlanningSummary.CURRENT_CONTRACT_VERSION,
                normalizedProfile.effectiveComplexity(),
                normalizedProfile.hardSignals(),
                normalizedProfile.shapeEnvelope()
        );
    }

    private PlanningModelPolicy resolvePlanningModelPolicy(
            final UUID userId,
            final PlanningComplexity complexity,
            final boolean docsOnlyFallback) {
        try {
            final ResolvedHeavyModel resolvedHeavyModel = heavyModelResolutionService.resolve(userId, null, null);
            return planningModelPolicyService.resolvePolicy(complexity, resolvedHeavyModel, docsOnlyFallback);
        } catch (final RuntimeException exception) {
            log.warn("Heavy model could not be resolved during preparation summary. Falling back to model-agnostic policy: userId={}, reason={}",
                    userId,
                    exception.getMessage());
            return planningModelPolicyService.resolvePolicy(complexity, null, docsOnlyFallback);
        }
    }

    private String buildProjectShapeSummary(final StructuredAnalysis analysis) {
        if (analysis == null) {
            return "";
        }

        final List<String> shapeParts = new ArrayList<>();
        if (analysis.projectType() != null && !analysis.projectType().isBlank()) {
            shapeParts.add(analysis.projectType());
        }

        final List<String> runtimeComponents = new ArrayList<>();
        if (analysis.frontendNeededResolved()) {
            runtimeComponents.add("frontend");
        }
        if (analysis.backendNeededResolved()) {
            runtimeComponents.add("backend");
        }
        if (analysis.workerNeededResolved()) {
            runtimeComponents.add("worker");
        }
        if (!runtimeComponents.isEmpty()) {
            shapeParts.add(String.join(" + ", runtimeComponents));
        }
        if (!analysis.deliveryChannels().isEmpty()) {
            shapeParts.add("channels: " + String.join(", ", analysis.deliveryChannels()));
        }
        return String.join(" | ", shapeParts);
    }

    private String buildStarterSummary(final StarterSelection starterSelection) {
        if (starterSelection.docsOnlyFallback()) {
            return "Docs-only fallback: " + starterSelection.docsOnlyFallbackReason();
        }
        if (starterSelection.recommendedStarter() == null) {
            return "";
        }
        return "%s (%s)".formatted(
                starterSelection.recommendedStarter().title(),
                starterSelection.recommendedStarter().key());
    }
}
