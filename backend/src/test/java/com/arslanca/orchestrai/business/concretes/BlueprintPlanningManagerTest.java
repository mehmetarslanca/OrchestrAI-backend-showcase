package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.abstracts.BlueprintGeneratorService;
import com.arslanca.orchestrai.business.abstracts.MultiPassBlueprintPlannerService;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityGateResult;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityVerdict;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairAction;
import com.arslanca.orchestrai.business.dtos.orchestration.RepairStatus;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlanningResult;
import com.arslanca.orchestrai.business.planning.governance.DefaultPlanningBudgetPolicy;
import com.arslanca.orchestrai.business.planning.governance.DefaultPlanningFallbackPolicy;
import com.arslanca.orchestrai.business.planning.governance.NoOpPlanningTelemetryService;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;

@ExtendWith(MockitoExtension.class)
class BlueprintPlanningManagerTest {

    @Mock
    private BlueprintGeneratorService blueprintGeneratorService;
    @Mock
    private MultiPassBlueprintPlannerService multiPassBlueprintPlannerService;
    @Mock
    private BlueprintQualityGateManager qualityGateManager;
    @Mock
    private BlueprintRepairPolicy repairPolicy;

    private final BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter = new BlueprintPlanCompatibilityAdapter();
    private BlueprintPlanningManager manager;

    @BeforeEach
    void setUp() {
        manager = new BlueprintPlanningManager(
                blueprintGeneratorService,
                multiPassBlueprintPlannerService,
                new DeterministicPlanningPolicyManager(),
                qualityGateManager,
                repairPolicy,
                new DefaultPlanningBudgetPolicy(),
                new DefaultPlanningFallbackPolicy(),
                new NoOpPlanningTelemetryService());
    }

    @Test
    void shouldUseRepairPolicyAndReturnTypedRepairOutcome() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "billing"),
                "complex",
                false,
                "",
                "Complex platform");
        final StarterSelection starterSelection = starterSelection();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                CapabilityRisk.ELEVATED,
                true,
                true,
                new PlannerModelRef("google:gemini-3-pro", ModelCapability.HIGH, false),
                DegradeStrategy.NONE);
        final CanonicalBlueprint initialPlan = canonicalBlueprint(new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes"));
        final CanonicalBlueprint repairedPlan = canonicalBlueprint(new BlueprintPlan("Acme Platform", "repaired overview", List.of(), List.of(), "rules", "notes"));
        final QualityGateResult failedResult = new QualityGateResult(
                QualityVerdict.TARGETED_EXPANSION,
                false,
                72,
                80,
                java.util.Map.of("phaseDepth", 8),
                List.of("Phase depth is weak"));
        final QualityGateResult passedResult = new QualityGateResult(
                QualityVerdict.ACCEPT,
                true,
                84,
                80,
                java.util.Map.of("phaseDepth", 16),
                List.of());

        when(multiPassBlueprintPlannerService.generateBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                false,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty())).thenReturn(initialPlan);
        when(qualityGateManager.evaluate(
                org.mockito.ArgumentMatchers.eq(initialPlan),
                org.mockito.ArgumentMatchers.eq(analysis),
                org.mockito.ArgumentMatchers.eq(planningModelPolicy),
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(failedResult);
        when(repairPolicy.maxRepairAttemptsFor(
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(2);
        when(repairPolicy.determineRepairAction(failedResult, initialPlan)).thenReturn(RepairAction.TARGETED_EXPANSION);
        when(multiPassBlueprintPlannerService.expandBlueprintDepth(
                analysis,
                "cursor",
                null,
                starterSelection,
                initialPlan,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty())).thenReturn(repairedPlan);
        when(qualityGateManager.evaluate(
                org.mockito.ArgumentMatchers.eq(repairedPlan),
                org.mockito.ArgumentMatchers.eq(analysis),
                org.mockito.ArgumentMatchers.eq(planningModelPolicy),
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(passedResult);

        final BlueprintPlanningResult result = manager.planBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                planningModelPolicy);

        assertThat(result.accepted()).isTrue();
        assertThat(result.repairStatus().status()).isEqualTo(RepairStatus.COMPLETED);
        assertThat(result.repairStatus().actions()).containsExactly(RepairAction.TARGETED_EXPANSION);
        assertThat(result.qualitySummary().policyVersionFingerprint().value()).contains("google:gemini-3-pro");
    }

    @Test
    void shouldReturnNotRequiredRepairStatusWhenFirstPassAlreadyPasses() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("spring", "postgres"),
                List.of("auth"),
                "moderate",
                false,
                "",
                "Simple platform");
        final StarterSelection starterSelection = starterSelection();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.SINGLE_PASS,
                CapabilityRisk.LOW,
                false,
                false,
                new PlannerModelRef("google:gemini-3-pro", ModelCapability.HIGH, false),
                DegradeStrategy.NONE);
        final CanonicalBlueprint initialPlan = canonicalBlueprint(new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes"));
        final QualityGateResult passedResult = new QualityGateResult(
                QualityVerdict.ACCEPT,
                true,
                62,
                60,
                java.util.Map.of("structuralCompleteness", 20),
                List.of());

        when(blueprintGeneratorService.generateBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty())).thenReturn(initialPlan);
        when(qualityGateManager.evaluate(
                org.mockito.ArgumentMatchers.eq(initialPlan),
                org.mockito.ArgumentMatchers.eq(analysis),
                org.mockito.ArgumentMatchers.eq(planningModelPolicy),
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(passedResult);
        when(repairPolicy.maxRepairAttemptsFor(
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(2);

        final BlueprintPlanningResult result = manager.planBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                planningModelPolicy);

        assertThat(result.accepted()).isTrue();
        assertThat(result.canonicalBlueprint().projectName()).isEqualTo("Acme Platform");
        assertThat(result.repairStatus().status()).isEqualTo(RepairStatus.NOT_REQUIRED);
        assertThat(result.repairStatus().actions()).isEmpty();
        verify(repairPolicy, never()).determineRepairAction(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldStopEarlyWhenRepairDoesNotImproveEnterprisePlan() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "billing"),
                "enterprise",
                false,
                "",
                "Enterprise platform");
        final StarterSelection starterSelection = starterSelection();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                CapabilityRisk.HIGH,
                true,
                true,
                new PlannerModelRef("google:gemini-3-pro", ModelCapability.HIGH, false),
                DegradeStrategy.NONE);
        final CanonicalBlueprint initialPlan = canonicalBlueprint(new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes"));
        final CanonicalBlueprint repairedPlan = canonicalBlueprint(new BlueprintPlan("Acme Platform", "same overview", List.of(), List.of(), "rules", "notes"));
        final QualityGateResult failedResult = new QualityGateResult(
                QualityVerdict.TARGETED_EXPANSION,
                false,
                78,
                85,
                java.util.Map.of("phaseDepth", 10),
                List.of("Phase depth is weak"));

        when(multiPassBlueprintPlannerService.generateBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                false,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty())).thenReturn(initialPlan);
        when(qualityGateManager.evaluate(
                org.mockito.ArgumentMatchers.eq(initialPlan),
                org.mockito.ArgumentMatchers.eq(analysis),
                org.mockito.ArgumentMatchers.eq(planningModelPolicy),
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(failedResult);
        when(repairPolicy.maxRepairAttemptsFor(
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(2);
        when(repairPolicy.determineRepairAction(failedResult, initialPlan)).thenReturn(RepairAction.TARGETED_EXPANSION);
        when(multiPassBlueprintPlannerService.expandBlueprintDepth(
                analysis,
                "cursor",
                null,
                starterSelection,
                initialPlan,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty())).thenReturn(repairedPlan);
        when(qualityGateManager.evaluate(
                org.mockito.ArgumentMatchers.eq(repairedPlan),
                org.mockito.ArgumentMatchers.eq(analysis),
                org.mockito.ArgumentMatchers.eq(planningModelPolicy),
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(failedResult);

        final BlueprintPlanningResult result = manager.planBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                planningModelPolicy);

        assertThat(result.accepted()).isFalse();
        assertThat(result.repairStatus().status()).isEqualTo(RepairStatus.REJECTED);
        assertThat(result.repairStatus().reason()).isEqualTo("no_quality_improvement");
        assertThat(result.repairStatus().actions()).containsExactly(RepairAction.TARGETED_EXPANSION);
    }

    @Test
    void shouldRouteAgentRegenerationThroughExpansionPath() {
        final BlueprintPlanningManager routingManager = new BlueprintPlanningManager(
                blueprintGeneratorService,
                multiPassBlueprintPlannerService,
                new DeterministicPlanningPolicyManager(),
                qualityGateManager,
                new BlueprintRepairPolicy() {
                    @Override
                    public int maxRepairAttemptsFor(
                            final com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile planningProfile) {
                        return 2;
                    }

                    @Override
                    public RepairAction determineRepairAction(
                            final QualityGateResult qualityGateResult,
                            final CanonicalBlueprint currentPlan) {
                        return RepairAction.AGENT_REGENERATION;
                    }
                },
                new DefaultPlanningBudgetPolicy(),
                new DefaultPlanningFallbackPolicy(),
                new NoOpPlanningTelemetryService());
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("spring", "postgres"),
                List.of("auth"),
                "moderate",
                false,
                "",
                "Simple platform");
        final StarterSelection starterSelection = starterSelection();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.SINGLE_PASS,
                CapabilityRisk.LOW,
                false,
                false,
                new PlannerModelRef("google:gemini-3-pro", ModelCapability.HIGH, false),
                DegradeStrategy.NONE);
        final CanonicalBlueprint initialPlan = canonicalBlueprint(new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes"));
        final CanonicalBlueprint repairedPlan = canonicalBlueprint(new BlueprintPlan("Acme Platform", "repaired overview", List.of(), List.of(), "rules", "notes"));
        final QualityGateResult failedResult = new QualityGateResult(
                QualityVerdict.TARGETED_EXPANSION,
                false,
                58,
                60,
                java.util.Map.of("structuralCompleteness", 20),
                List.of("Agent count is low"));
        final QualityGateResult passedResult = new QualityGateResult(
                QualityVerdict.ACCEPT,
                true,
                65,
                60,
                java.util.Map.of("structuralCompleteness", 20),
                List.of());

        when(blueprintGeneratorService.generateBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty())).thenReturn(initialPlan);
        when(qualityGateManager.evaluate(
                org.mockito.ArgumentMatchers.eq(initialPlan),
                org.mockito.ArgumentMatchers.eq(analysis),
                org.mockito.ArgumentMatchers.eq(planningModelPolicy),
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(failedResult);
        when(multiPassBlueprintPlannerService.expandBlueprintDepth(
                analysis,
                "cursor",
                null,
                starterSelection,
                initialPlan,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty())).thenReturn(repairedPlan);
        when(qualityGateManager.evaluate(
                org.mockito.ArgumentMatchers.eq(repairedPlan),
                org.mockito.ArgumentMatchers.eq(analysis),
                org.mockito.ArgumentMatchers.eq(planningModelPolicy),
                org.mockito.ArgumentMatchers.any(com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile.class))).thenReturn(passedResult);

        final BlueprintPlanningResult result = routingManager.planBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                planningModelPolicy);

        assertThat(result.repairStatus().actions()).containsExactly(RepairAction.AGENT_REGENERATION);
        verify(multiPassBlueprintPlannerService).expandBlueprintDepth(
                analysis,
                "cursor",
                null,
                starterSelection,
                initialPlan,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty());
        verify(multiPassBlueprintPlannerService, never()).generateBlueprint(
                analysis,
                "cursor",
                null,
                starterSelection,
                false,
                com.arslanca.orchestrai.business.runtime.GenerationUsageContext.empty());
    }

    private CanonicalBlueprint canonicalBlueprint(final BlueprintPlan blueprintPlan) {
        return blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(blueprintPlan);
    }

    private StarterSelection starterSelection() {
        return new StarterSelection(
                java.util.UUID.randomUUID(),
                "Acme Platform",
                "cursor",
                new StarterCandidate(
                        "nextjs-saas-shell",
                        "Next.js SaaS Shell",
                        "saas",
                        StarterSourceKind.REPO_PACK,
                        "repo-pack",
                        List.of("nextjs", "postgres"),
                        "Best match for requested stack."),
                List.of(),
                false,
                "");
    }
}
