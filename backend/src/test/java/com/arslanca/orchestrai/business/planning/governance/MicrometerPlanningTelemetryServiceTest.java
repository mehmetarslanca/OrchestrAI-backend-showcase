package com.arslanca.orchestrai.business.planning.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningActualShape;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
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
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerPlanningTelemetryServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private MicrometerPlanningTelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        telemetryService = new MicrometerPlanningTelemetryService(meterRegistry);
    }

    @Test
    void shouldRecordFinalPreviewQualityShapeAndRolloutMetrics() {
        final PlanningGovernanceContext context = governanceContext();
        final PlanningGovernanceSnapshot snapshot = governanceSnapshot();
        final PlanningQualitySummary qualitySummary = qualitySummary();
        final PlanningRepairOutcome repairOutcome = repairOutcome();

        telemetryService.recordSnapshot(context, snapshot);
        telemetryService.recordPreviewFinalized(snapshot, PlanningPreviewStatus.REJECTED, qualitySummary, repairOutcome);
        telemetryService.recordStartReuse(snapshot, true);

        assertThat(meterRegistry.counter(
                "planning.governance.snapshot",
                "complexity", "complex",
                "effectiveComplexity", "enterprise",
                "planningMode", "multi_pass_expansion",
                "envelope", "enterprise_partial",
                "rolloutMode", "shadow",
                "rolloutReason", "shadow_mode",
                "localModel", "false",
                "uplifted", "true",
                "upliftReason", "multi-tenancy").count()).isEqualTo(1.0d);

        assertThat(meterRegistry.counter(
                "planning.preview.final_quality",
                "complexity", "complex",
                "effectiveComplexity", "enterprise",
                "planningMode", "multi_pass_expansion",
                "rolloutMode", "shadow",
                "previewStatus", "rejected",
                "verdict", "full_regenerate",
                "repairStatus", "rejected",
                "repairAction", "full_regenerate",
                "reasonSource", "quality_finding",
                "reasonCode", "under_min_phase_count").count()).isEqualTo(1.0d);

        assertThat(shapeSummary("planning.preview.shape.expected", "phases", "bound", "min").totalAmount()).isEqualTo(8.0d);
        assertThat(shapeSummary("planning.preview.shape.actual", "phases").totalAmount()).isEqualTo(2.0d);
        assertThat(shapeSummary("planning.preview.shape.ratio", "phases", "reference", "min").totalAmount()).isEqualTo(0.25d);

        assertThat(meterRegistry.counter(
                "planning.start.reuse",
                "complexity", "complex",
                "effectiveComplexity", "enterprise",
                "planningMode", "multi_pass_expansion",
                "rolloutMode", "shadow",
                "mode", "accepted_preview",
                "billingDisposition", "accepted_preview_generation_billed_once").count()).isEqualTo(1.0d);
    }

    @Test
    void shouldRecordCompactRescueAndPreviewInvalidationWithBoundedTags() {
        final PlanningGovernanceSnapshot snapshot = governanceSnapshot();

        telemetryService.recordPreviewInvalidation(snapshot, PlanningPreviewStatus.ACCEPTED, "accepted_preview_policy_drift");
        telemetryService.recordCompactRescueUsage(
                "phase expansion",
                PlanningComplexity.COMPLEX,
                new PlannerModelRef("google:gemini-3-pro", ModelCapability.HIGH, false),
                "compact_phase_expansion",
                true);

        assertThat(meterRegistry.counter(
                "planning.preview.invalidation",
                "complexity", "complex",
                "effectiveComplexity", "enterprise",
                "planningMode", "multi_pass_expansion",
                "rolloutMode", "shadow",
                "previousStatus", "accepted",
                "reason", "accepted_preview_policy_drift").count()).isEqualTo(1.0d);

        assertThat(meterRegistry.counter(
                "planning.pass.compact_rescue",
                "passName", "phase_expansion",
                "complexity", "complex",
                "provider", "google",
                "localModel", "false",
                "rescuePath", "compact_phase_expansion",
                "outcome", "success").count()).isEqualTo(1.0d);
    }

    private DistributionSummary shapeSummary(
            final String meterName,
            final String dimension,
            final String... extraTags) {
        final java.util.List<String> tags = new java.util.ArrayList<>(List.of(
                "dimension", dimension,
                "complexity", "complex",
                "effectiveComplexity", "enterprise",
                "rolloutMode", "shadow",
                "verdict", "full_regenerate",
                "repairAction", "full_regenerate",
                "previewStatus", "rejected"));
        tags.addAll(List.of(extraTags));
        return meterRegistry.get(meterName).tags(tags.toArray(String[]::new)).summary();
    }

    private PlanningGovernanceContext governanceContext() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "Acme Analytics",
                "analytics dashboard platform",
                List.of("spring-boot", "postgres", "react"),
                List.of("event ingestion", "reporting api", "admin dashboard"),
                "complex",
                false,
                "",
                "Enterprise-style analytics platform",
                List.of("org admin"),
                List.of("serve multiple organizations"),
                List.of("ingest events", "query reports"),
                List.of("web"),
                true,
                true,
                true,
                "self-hosted",
                "multi-tenant",
                List.of("postgres"),
                List.of("segment", "stripe"),
                List.of("rbac"),
                List.of("soc2"),
                "10k orgs",
                "sub-second dashboard reads",
                List.of("air-gapped install"),
                List.of("assume basic auth"),
                List.of("retention policy TBD"),
                List.of("event cardinality?"),
                List.of("customer deployment topology"),
                List.of("native mobile"),
                List.of("multi-tenant", "async-processing", "dashboard-and-api"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
        return new PlanningGovernanceContext(
                null,
                analysis,
                new DeterministicPlanningProfile(
                        PlanningComplexity.ENTERPRISE,
                        List.of(PlanningHardSignal.MULTI_TENANCY, PlanningHardSignal.ASYNC_PROCESSING),
                        shapeEnvelope(),
                        List.of("Enterprise uplift applied")),
                "spring-boot-postgres-api",
                "cursor",
                new PlanningModelPolicy(
                        PlanningMode.MULTI_PASS_EXPANSION,
                        CapabilityRisk.HIGH.value(),
                        true,
                        true,
                        "google:gemini-3-pro",
                        ModelCapability.HIGH,
                        false,
                        DegradeStrategy.NONE.value()),
                "1.0");
    }

    private PlanningGovernanceSnapshot governanceSnapshot() {
        return new PlanningGovernanceSnapshot(
                PlanningGovernanceSnapshot.CURRENT_SCHEMA_VERSION,
                PlanningComplexity.COMPLEX,
                PlanningComplexity.ENTERPRISE,
                shapeEnvelope(),
                PlanningMode.MULTI_PASS_EXPANSION,
                new PlannerModelRef("google:gemini-3-pro", ModelCapability.HIGH, false),
                new PolicyVersionFingerprint("planning-v2"),
                new PolicyVersionFingerprint("governance-v2"),
                "spring-boot-postgres-api",
                "cursor",
                DegradeStrategy.NONE,
                "2.0",
                "1.0",
                new PlanningBudgetDecision(true, "enterprise_partial", 2, 1, true, true, ""),
                new PreviewBillingDecision(
                        "preview_generated_not_billed",
                        "preview_cache_hit_not_billed",
                        "accepted_preview_generation_billed_once",
                        "fresh_planning_generation_billed_once",
                        false),
                new PreviewCacheDecision("cache-key", "planner-fingerprint", "governance-fingerprint", 120L, 240L, true),
                new PlanningRolloutDecision(PlanningRolloutMode.SHADOW, true, false, false, "shadow_mode"));
    }

    private PlanningQualitySummary qualitySummary() {
        return new PlanningQualitySummary(
                PlanningComplexity.COMPLEX,
                List.of("multi-tenant", "async-processing"),
                CapabilityRisk.HIGH,
                true,
                true,
                new PlannerModelRef("google:gemini-3-pro", ModelCapability.HIGH, false),
                DegradeStrategy.NONE,
                false,
                58,
                75,
                QualityVerdict.FULL_REGENERATE,
                false,
                Map.of(QualityScoreCategory.STRUCTURAL_COMPLETENESS, 18),
                PlanningComplexity.ENTERPRISE,
                new PlanningShapeAssessment(
                        shapeEnvelope(),
                        new PlanningActualShape(
                                2,
                                2,
                                2,
                                List.of(PlanningCoverageFamily.BACKEND_FOUNDATION),
                                List.of("Operator experience")),
                        false,
                        false,
                        false,
                        List.of(PlanningCoverageFamily.FRONTEND_EXPERIENCE, PlanningCoverageFamily.ASYNC_PROCESSING)),
                List.of(new PlanningQualityFinding(
                        PlanningQualityFindingCode.UNDER_MIN_PHASE_COUNT,
                        PlanningQualityFindingType.SHAPE_DEFICIT,
                        "Phase count fell below the deterministic floor.")),
                List.of("Phase count fell below the deterministic floor."),
                new PolicyVersionFingerprint("planning-v2"));
    }

    private PlanningRepairOutcome repairOutcome() {
        return new PlanningRepairOutcome(
                RepairStatus.REJECTED,
                2,
                2,
                List.of(RepairAction.TARGETED_EXPANSION, RepairAction.FULL_REGENERATE),
                QualityVerdict.FULL_REGENERATE,
                58,
                75,
                List.of("Phase count fell below the deterministic floor."),
                "repair_budget_exhausted");
    }

    private PlanningShapeEnvelope shapeEnvelope() {
        return new PlanningShapeEnvelope(
                4,
                7,
                8,
                12,
                5,
                10,
                List.of(
                        PlanningCoverageFamily.FRONTEND_EXPERIENCE,
                        PlanningCoverageFamily.BACKEND_FOUNDATION,
                        PlanningCoverageFamily.ASYNC_PROCESSING),
                "4-7 workstreams | 8-12 phases | 5-10 agents",
                "Enterprise scope benefits from a stronger planner model.");
    }
}
