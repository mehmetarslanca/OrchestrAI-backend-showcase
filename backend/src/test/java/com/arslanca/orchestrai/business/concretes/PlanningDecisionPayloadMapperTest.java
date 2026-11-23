package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningActualShape;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFinding;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingCode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingType;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeAssessment;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.PolicyVersionFingerprint;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityScoreCategory;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityVerdict;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairAction;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairStatus;

class PlanningDecisionPayloadMapperTest {

    @Test
    void shouldRoundTripTypedDecisionPayloadsWithoutLosingLegacyShape() {
        final PlanningShapeAssessment shapeAssessment = new PlanningShapeAssessment(
                new PlanningShapeEnvelope(
                        2,
                        4,
                        4,
                        7,
                        2,
                        5,
                        List.of(PlanningCoverageFamily.FRONTEND_EXPERIENCE, PlanningCoverageFamily.BACKEND_FOUNDATION),
                        null,
                        ""),
                new PlanningActualShape(
                        2,
                        4,
                        2,
                        List.of(PlanningCoverageFamily.FRONTEND_EXPERIENCE, PlanningCoverageFamily.BACKEND_FOUNDATION),
                        List.of()),
                true,
                true,
                true,
                List.of());
        final PlanningQualitySummary qualitySummary = new PlanningQualitySummary(
                PlanningComplexity.COMPLEX,
                List.of("multi-tenant", "event-driven"),
                CapabilityRisk.ELEVATED,
                true,
                true,
                new PlannerModelRef("google:gemini-3-pro", ModelCapability.HIGH, false),
                DegradeStrategy.NONE,
                false,
                88,
                80,
                QualityVerdict.ACCEPT,
                true,
                Map.of(
                        QualityScoreCategory.STRUCTURAL_COMPLETENESS, 20,
                        QualityScoreCategory.SCOPE_COVERAGE, 18),
                PlanningComplexity.ENTERPRISE,
                shapeAssessment,
                List.of(new PlanningQualityFinding(
                        PlanningQualityFindingCode.UNDER_MIN_PHASE_COUNT,
                        PlanningQualityFindingType.SHAPE_DEFICIT,
                        "Expected at least 8 phases.")),
                List.of("No blocking findings"),
                new PolicyVersionFingerprint("fingerprint:v1"));
        final PlanningRepairOutcome repairOutcome = new PlanningRepairOutcome(
                RepairStatus.COMPLETED,
                1,
                2,
                List.of(RepairAction.TARGETED_EXPANSION),
                QualityVerdict.ACCEPT,
                88,
                80,
                List.of("Phase depth repaired"),
                "");

        final Map<String, Object> qualityPayload = PlanningDecisionPayloadMapper.toLegacyQualitySummary(qualitySummary);
        final Map<String, Object> repairPayload = PlanningDecisionPayloadMapper.toLegacyRepairStatus(repairOutcome);

        assertThat(qualityPayload).containsEntry("capabilityRisk", "elevated");
        assertThat(qualityPayload).containsEntry("degradeStrategy", "none");
        assertThat(qualityPayload).containsEntry("effectiveComplexity", "enterprise");
        assertThat(qualityPayload).containsKey("shapeAssessment");
        assertThat(qualityPayload).containsKey("deterministicFindings");
        assertThat(repairPayload).containsEntry("status", "completed");
        assertThat(repairPayload).containsEntry("actions", List.of("targeted_expansion"));

        assertThat(PlanningDecisionPayloadMapper.fromLegacyQualitySummary(qualityPayload)).isEqualTo(qualitySummary);
        assertThat(PlanningDecisionPayloadMapper.fromLegacyRepairStatus(repairPayload)).isEqualTo(repairOutcome);
    }

    @Test
    void shouldApplyExplicitFallbacksForUnknownLegacyDecisionValues() {
        final PlanningQualitySummary qualitySummary = PlanningDecisionPayloadMapper.fromLegacyQualitySummary(Map.of(
                "complexity", "complex",
                "capabilityRisk", "legacy-risk",
                "degradeStrategy", "legacy-strategy",
                "qualityVerdict", "legacy-verdict",
                "categoryScores", Map.of("legacy-category", 4)));
        final PlanningRepairOutcome repairOutcome = PlanningDecisionPayloadMapper.fromLegacyRepairStatus(Map.of(
                "status", "legacy-status",
                "actions", List.of("legacy-action"),
                "finalVerdict", "legacy-verdict"));

        assertThat(qualitySummary.capabilityRisk()).isEqualTo(CapabilityRisk.UNKNOWN);
        assertThat(qualitySummary.degradeStrategy()).isEqualTo(DegradeStrategy.UNKNOWN);
        assertThat(qualitySummary.qualityVerdict()).isEqualTo(QualityVerdict.REJECT);
        assertThat(qualitySummary.effectiveComplexity()).isEqualTo(PlanningComplexity.COMPLEX);
        assertThat(qualitySummary.categoryScores()).containsEntry(QualityScoreCategory.UNKNOWN, 4);

        assertThat(repairOutcome.status()).isEqualTo(RepairStatus.UNKNOWN);
        assertThat(repairOutcome.actions()).containsExactly(RepairAction.UNKNOWN);
        assertThat(repairOutcome.finalVerdict()).isEqualTo(QualityVerdict.REJECT);
    }
}
