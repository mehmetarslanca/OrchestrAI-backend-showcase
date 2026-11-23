package com.arslanca.orchestrai.business.planning.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary;
import com.arslanca.orchestrai.business.dtos.orchestration.RequirementCompletenessReport;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

class PreparationSnapshotCodecTest {

    private final PreparationSnapshotCodec codec = new PreparationSnapshotCodec(new ObjectMapper());

    @Test
    void shouldRoundTripPreparationSnapshotThroughTypedReaders() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "projects"),
                "moderate",
                false,
                "",
                "AI orchestration workspace");
        final StarterSelection starterSelection = new StarterSelection(
                UUID.randomUUID(),
                "Acme Platform",
                "cursor",
                new StarterCandidate(
                        "nextjs-saas-shell",
                        "Next.js SaaS Shell",
                        "saas",
                        StarterSourceKind.REPO_PACK,
                        "repo-pack",
                        List.of("nextjs", "postgres"),
                        "Best match."),
                List.of(),
                false,
                "");
        final PlanningSummary planningSummary = new PlanningSummary(
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity.COMPLEX,
                List.of("multi-tenant"),
                "saas | frontend + backend",
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk.ELEVATED,
                true,
                true,
                false,
                "Next.js SaaS Shell (nextjs-saas-shell)",
                "google:gemini-3-pro",
                ModelCapability.HIGH,
                false,
                com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy.NONE,
                PlanningSummary.CURRENT_CONTRACT_VERSION,
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity.ENTERPRISE,
                List.of(PlanningHardSignal.MULTI_TENANCY, PlanningHardSignal.ASYNC_PROCESSING),
                new PlanningShapeEnvelope(
                        4,
                        7,
                        8,
                        12,
                        5,
                        10,
                        List.of(
                                PlanningCoverageFamily.TENANCY_AND_ISOLATION,
                                PlanningCoverageFamily.ASYNC_PROCESSING,
                                PlanningCoverageFamily.DATA_AND_STORAGE),
                        "4-7 workstreams | 8-12 phases | 5-10 agents",
                        "Enterprise scope detected; a stronger planner model is recommended."));
        final RequirementCompletenessReport completenessReport = new RequirementCompletenessReport(
                true,
                List.of(),
                List.of(),
                List.of(),
                RequirementCompletenessReport.CURRENT_CONTRACT_VERSION);
        final ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel(
                "google:gemini-3-pro",
                "google",
                "gemini-3-pro",
                com.arslanca.orchestrai.core.ai.provider.CredentialSource.PLATFORM,
                "platform",
                "gemini-3-pro",
                "Gemini 3 Pro",
                false,
                false,
                null,
                null,
                null,
                List.of(),
                ModelCapability.HIGH);
        final ProjectGenerationPreparationEntity preparation = ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(),
                null,
                null,
                "WEB",
                "Acme Platform",
                "cursor",
                "Build an AI orchestration workspace",
                codec.toPayload(analysis),
                codec.toPayload(starterSelection),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION,
                codec.buildPreparationSnapshot(
                        analysis,
                        planningSummary,
                        starterSelection,
                        completenessReport,
                        resolvedHeavyModel,
                        java.util.Optional.empty(),
                        null));

        assertThat(codec.readStructuredAnalysis(preparation).projectName()).isEqualTo("Acme Platform");
        assertThat(codec.readStarterSelection(preparation).recommendedStarter().key()).isEqualTo("nextjs-saas-shell");
        assertThat(codec.readPlanningSummary(preparation)).isEqualTo(planningSummary);
        assertThat(codec.readResolvedHeavyModel(preparation)).isEqualTo(resolvedHeavyModel);
        assertThat(codec.buildPreparationSnapshot(
                analysis,
                planningSummary,
                starterSelection,
                completenessReport,
                resolvedHeavyModel,
                java.util.Optional.empty(),
                null)).containsKeys("structuredAnalysis", "planningSummary", "starterSelection", "requirementCompleteness", "resolvedHeavyModel");
        assertThat(codec.toPayload(Map.of("key", "value"))).containsEntry("key", "value");
    }
}
