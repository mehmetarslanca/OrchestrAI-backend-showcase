package com.arslanca.orchestrai.business.planning.governance;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;

@Component
public class PropertyBackedPlanningRolloutPolicy implements PlanningRolloutPolicy {

    private final PlanningGovernanceProperties properties;

    public PropertyBackedPlanningRolloutPolicy(final PlanningGovernanceProperties properties) {
        this.properties = properties;
    }

    @Override
    public PlanningRolloutDecision resolveDecision(final PlanningGovernanceContext context) {
        if (properties.isKillSwitch()) {
            return new PlanningRolloutDecision(PlanningRolloutMode.OFF, false, true, false, "kill_switch_active");
        }

        final PlanningRolloutMode configuredMode = PlanningRolloutMode.fromValue(properties.getRolloutMode());
        final PlanningComplexity minimumComplexity = PlanningComplexity.fromValue(properties.getMinimumComplexity());
        final PlanningComplexity actualComplexity = context == null || context.structuredAnalysis() == null
                ? PlanningComplexity.MODERATE
                : context.structuredAnalysis().planningComplexity();
        if (actualComplexity.ordinal() < minimumComplexity.ordinal()) {
            return new PlanningRolloutDecision(PlanningRolloutMode.OFF, false, false, false, "complexity_below_minimum");
        }

        if (context != null
                && context.planningModelPolicy() != null
                && context.planningModelPolicy().localModel()
                && !properties.isIncludeLocalModels()) {
            return new PlanningRolloutDecision(PlanningRolloutMode.OFF, false, false, false, "local_model_excluded");
        }

        final boolean cohortMatched = cohortMatched(context == null || context.preparation() == null
                ? null
                : context.preparation().getId());

        return switch (configuredMode) {
            case OFF -> new PlanningRolloutDecision(PlanningRolloutMode.OFF, cohortMatched, false, false, "rollout_disabled");
            case SHADOW -> new PlanningRolloutDecision(PlanningRolloutMode.SHADOW, cohortMatched, false, false, "shadow_mode");
            case ENFORCED -> cohortMatched
                    ? new PlanningRolloutDecision(PlanningRolloutMode.ENFORCED, true, false, true, "cohort_enforced")
                    : new PlanningRolloutDecision(PlanningRolloutMode.SHADOW, false, false, false, "cohort_shadow_only");
        };
    }

    @Override
    public String policyVersion() {
        return String.join(
                ":",
                "rollout-v1",
                PlanningRolloutMode.fromValue(properties.getRolloutMode()).value(),
                PlanningComplexity.fromValue(properties.getMinimumComplexity()).name().toLowerCase(),
                "pct-" + properties.getRolloutPercentage(),
                properties.isIncludeLocalModels() ? "local-on" : "local-off",
                properties.isKillSwitch() ? "kill-on" : "kill-off");
    }

    private boolean cohortMatched(final UUID preparationId) {
        final int rolloutPercentage = Math.max(0, Math.min(100, properties.getRolloutPercentage()));
        if (rolloutPercentage >= 100) {
            return true;
        }
        if (rolloutPercentage <= 0) {
            return false;
        }
        if (preparationId == null) {
            return true;
        }
        final long hashedValue = Integer.toUnsignedLong(preparationId.hashCode()) % 100L;
        return hashedValue < rolloutPercentage;
    }
}
