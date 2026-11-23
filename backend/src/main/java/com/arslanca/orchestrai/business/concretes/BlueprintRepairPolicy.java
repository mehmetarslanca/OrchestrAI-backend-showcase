package com.arslanca.orchestrai.business.concretes;

import java.util.List;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFinding;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingType;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityGateResult;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityGateThresholdProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityVerdict;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairAction;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;

@Service
public class BlueprintRepairPolicy {

    public int maxRepairAttemptsFor(final DeterministicPlanningProfile planningProfile) {
        return QualityGateThresholdProfile.forProfile(planningProfile).maxRepairAttempts();
    }

    public int maxRepairAttemptsFor(final PlanningComplexity complexity) {
        return QualityGateThresholdProfile.forComplexity(complexity).maxRepairAttempts();
    }

    public RepairAction determineRepairAction(
            final QualityGateResult qualityGateResult,
            final CanonicalBlueprint currentPlan) {
        if (qualityGateResult == null) {
            return RepairAction.FULL_REGENERATE;
        }

        final List<PlanningQualityFinding> findings = qualityGateResult.deterministicFindings();
        if (hasFindingType(findings, PlanningQualityFindingType.SHAPE_DEFICIT)
                || hasFindingType(findings, PlanningQualityFindingType.COVERAGE_DEFICIT)
                || hasFindingType(findings, PlanningQualityFindingType.STARTER_CONFLICT)) {
            return RepairAction.FULL_REGENERATE;
        }
        if (hasFindingType(findings, PlanningQualityFindingType.RULES_DEFICIT)) {
            return RepairAction.RULES_REPAIR;
        }
        if (hasFindingType(findings, PlanningQualityFindingType.AGENT_DEFICIT)) {
            return RepairAction.AGENT_REGENERATION;
        }
        if (hasFindingType(findings, PlanningQualityFindingType.DEPTH_DEFICIT)
                || hasFindingType(findings, PlanningQualityFindingType.RISK_DEFICIT)) {
            return RepairAction.TARGETED_EXPANSION;
        }

        if (qualityGateResult.verdict() == QualityVerdict.TARGETED_EXPANSION
                && currentPlan != null
                && !currentPlan.workstreams().isEmpty()) {
            return RepairAction.TARGETED_EXPANSION;
        }
        return RepairAction.FULL_REGENERATE;
    }

    private boolean hasFindingType(
            final List<PlanningQualityFinding> findings,
            final PlanningQualityFindingType type) {
        return findings.stream().anyMatch(finding -> finding.type() == type);
    }
}
