package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFinding;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingCode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingType;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeAssessment;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityGateResult;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityVerdict;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairAction;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;

class BlueprintRepairPolicyTest {

    private final BlueprintRepairPolicy repairPolicy = new BlueprintRepairPolicy();
    private final BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter = new BlueprintPlanCompatibilityAdapter();

    @Test
    void shouldReturnExpectedRepairActionsForTypedDeficits() {
        assertThat(repairPolicy.determineRepairAction(
                qualityGateResult(PlanningQualityFindingType.SHAPE_DEFICIT, PlanningQualityFindingCode.UNDER_MIN_PHASE_COUNT),
                canonicalPlan()))
                .isEqualTo(RepairAction.FULL_REGENERATE);

        assertThat(repairPolicy.determineRepairAction(
                qualityGateResult(PlanningQualityFindingType.DEPTH_DEFICIT, PlanningQualityFindingCode.WEAK_PHASE_DEPTH),
                canonicalPlan()))
                .isEqualTo(RepairAction.TARGETED_EXPANSION);

        assertThat(repairPolicy.determineRepairAction(
                qualityGateResult(PlanningQualityFindingType.AGENT_DEFICIT, PlanningQualityFindingCode.UNDER_MIN_AGENT_COUNT),
                canonicalPlan()))
                .isEqualTo(RepairAction.AGENT_REGENERATION);

        assertThat(repairPolicy.determineRepairAction(
                qualityGateResult(PlanningQualityFindingType.RULES_DEFICIT, PlanningQualityFindingCode.IDE_RULES_CONFLICT),
                canonicalPlan()))
                .isEqualTo(RepairAction.RULES_REPAIR);
    }

    @Test
    void shouldFallbackToFullRegenerateWhenUsableWorkstreamsAreMissing() {
        final QualityGateResult qualityGateResult = new QualityGateResult(
                QualityVerdict.TARGETED_EXPANSION,
                false,
                65,
                80,
                Map.of(),
                List.of("Scope coverage is weak"));
        final BlueprintPlan blueprintPlan = new BlueprintPlan(
                "Acme Platform",
                "Executive summary",
                "overview",
                "System context",
                List.of("Modular monolith"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "rules",
                "notes");

        assertThat(repairPolicy.determineRepairAction(
                qualityGateResult,
                blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(blueprintPlan)))
                .isEqualTo(RepairAction.FULL_REGENERATE);
        assertThat(repairPolicy.maxRepairAttemptsFor(PlanningComplexity.COMPLEX)).isEqualTo(2);
    }

    private QualityGateResult qualityGateResult(
            final PlanningQualityFindingType type,
            final PlanningQualityFindingCode code) {
        return new QualityGateResult(
                QualityVerdict.TARGETED_EXPANSION,
                false,
                72,
                80,
                Map.of(),
                List.of("deterministic finding"),
                List.of(new PlanningQualityFinding(code, type, "deterministic finding")),
                PlanningShapeAssessment.unknown());
    }

    private com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint canonicalPlan() {
        return blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(new BlueprintPlan(
                "Acme Platform",
                "Executive summary",
                "overview",
                "System context",
                List.of("Modular monolith"),
                List.of(new BlueprintPlan.WorkstreamDefinition(
                        "Backend",
                        "Ship API",
                        "Needed for delivery",
                        List.of("api"),
                        List.of(),
                        List.of(),
                        List.of("backend-engineer"),
                        List.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "rules",
                "notes"));
    }
}
