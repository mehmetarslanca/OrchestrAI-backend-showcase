package com.arslanca.orchestrai.business.concretes;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;

@Service
public class PlanningModelPolicyManager implements PlanningModelPolicyService {

    @Override
    public PlanningModelPolicy resolvePolicy(
            final PlanningComplexity complexity,
            final ResolvedHeavyModel resolvedHeavyModel,
            final boolean docsOnlyFallback) {
        final PlanningComplexity normalizedComplexity = complexity == null ? PlanningComplexity.MODERATE : complexity;
        final boolean localModel = resolvedHeavyModel != null && resolvedHeavyModel.isLocalModel();
        final ModelCapability modelCapability = resolvedHeavyModel == null
                ? ModelCapability.UNKNOWN
                : resolvedHeavyModel.capability();

        final PlanningMode selectedPlanningMode = resolvePlanningMode(normalizedComplexity, modelCapability, localModel);
        final boolean previewRequired = normalizedComplexity.requiresPreviewReview();
        final boolean previewRecommended = previewRequired || docsOnlyFallback;
        final CapabilityRisk capabilityRisk = resolveCapabilityRisk(
                normalizedComplexity,
                modelCapability,
                localModel,
                docsOnlyFallback,
                resolvedHeavyModel);
        final DegradeStrategy degradeStrategy = resolveDegradeStrategy(
                normalizedComplexity,
                modelCapability,
                localModel,
                resolvedHeavyModel);

        return new PlanningModelPolicy(
                selectedPlanningMode,
                capabilityRisk,
                previewRecommended,
                previewRequired,
                new PlannerModelRef(resolvedHeavyModel == null ? "" : resolvedHeavyModel.modelKey(), modelCapability, localModel),
                degradeStrategy);
    }

    private PlanningMode resolvePlanningMode(
            final PlanningComplexity complexity,
            final ModelCapability modelCapability,
            final boolean localModel) {
        return switch (complexity) {
            case SIMPLE -> PlanningMode.SINGLE_PASS;
            case MODERATE -> modelCapability == ModelCapability.LOW && localModel
                    ? PlanningMode.SINGLE_PASS
                    : PlanningMode.REDUCED_MULTI_PASS;
            case COMPLEX -> modelCapability == ModelCapability.LOW && localModel
                    ? PlanningMode.REDUCED_MULTI_PASS
                    : PlanningMode.MULTI_PASS_EXPANSION;
            case ENTERPRISE -> modelCapability == ModelCapability.HIGH
                    ? PlanningMode.MULTI_PASS_EXPANSION
                    : PlanningMode.REDUCED_MULTI_PASS;
        };
    }

    private CapabilityRisk resolveCapabilityRisk(
            final PlanningComplexity complexity,
            final ModelCapability modelCapability,
            final boolean localModel,
            final boolean docsOnlyFallback,
            final ResolvedHeavyModel resolvedHeavyModel) {
        if (resolvedHeavyModel == null) {
            return complexity.requiresPreviewReview() ? CapabilityRisk.HIGH : CapabilityRisk.MODERATE;
        }

        return switch (complexity) {
            case SIMPLE -> docsOnlyFallback || modelCapability == ModelCapability.LOW ? CapabilityRisk.MODERATE : CapabilityRisk.LOW;
            case MODERATE -> modelCapability == ModelCapability.LOW && localModel ? CapabilityRisk.ELEVATED : CapabilityRisk.MODERATE;
            case COMPLEX -> modelCapability == ModelCapability.LOW && localModel ? CapabilityRisk.HIGH : CapabilityRisk.ELEVATED;
            case ENTERPRISE -> modelCapability == ModelCapability.HIGH && !docsOnlyFallback ? CapabilityRisk.HIGH : CapabilityRisk.CRITICAL;
        };
    }

    private DegradeStrategy resolveDegradeStrategy(
            final PlanningComplexity complexity,
            final ModelCapability modelCapability,
            final boolean localModel,
            final ResolvedHeavyModel resolvedHeavyModel) {
        if (resolvedHeavyModel == null) {
            return DegradeStrategy.PLANNER_MODEL_UNAVAILABLE;
        }
        if (complexity == PlanningComplexity.ENTERPRISE && modelCapability != ModelCapability.HIGH) {
            return DegradeStrategy.ENTERPRISE_QUALITY_WARNING;
        }
        if (complexity.requiresPreviewReview() && localModel && modelCapability == ModelCapability.LOW) {
            return DegradeStrategy.SKELETON_FIRST_PREVIEW;
        }
        if (complexity == PlanningComplexity.MODERATE && localModel && modelCapability == ModelCapability.LOW) {
            return DegradeStrategy.REDUCED_PREVIEW;
        }
        return DegradeStrategy.NONE;
    }
}
