package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.abstracts.BlueprintGeneratorService;
import com.arslanca.orchestrai.business.abstracts.BlueprintPlanningService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.abstracts.PromptAnalyzerService;
import com.arslanca.orchestrai.business.abstracts.StarterResolutionService;
import com.arslanca.orchestrai.business.concretes.cancellation.GenerationCancellationToken;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlanningResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStage;
import com.arslanca.orchestrai.business.dtos.projectgeneration.OrchestrationResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;

@ExtendWith(MockitoExtension.class)
class OrchestrationManagerTest {

    private static final BlueprintPlanCompatibilityAdapter BLUEPRINT_PLAN_COMPATIBILITY_ADAPTER = new BlueprintPlanCompatibilityAdapter();

    private static final ResolvedHeavyModel RESOLVED_HEAVY_MODEL = new ResolvedHeavyModel(
            "google:gemini-3-pro",
            "google",
            "gemini-3-pro",
            com.arslanca.orchestrai.core.ai.provider.CredentialSource.PLATFORM,
            "platform",
            "gemini-3-pro",
            "Gemini 3 Pro");

    @Mock
    private PromptAnalyzerService promptAnalyzerService;

    @Mock
    private BlueprintPlanningService blueprintPlanningService;

    @Mock
    private PlanningModelPolicyService planningModelPolicyService;

    @Mock
    private StarterResolutionService starterResolutionService;

    private OrchestrationManager orchestrationManager;
    private DeterministicPlanningPolicyManager deterministicPlanningPolicyManager;

    @BeforeEach
    void setUp() {
        deterministicPlanningPolicyManager = new DeterministicPlanningPolicyManager();
        orchestrationManager = new OrchestrationManager(
                promptAnalyzerService,
                blueprintPlanningService,
                planningModelPolicyService,
                deterministicPlanningPolicyManager,
                starterResolutionService);
    }

    @Test
    @DisplayName("Happy path: should emit ANALYZING and PLANNING_BLUEPRINT progress and return plan")
    void shouldReportProgressAndReturnBlueprintPlan() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "test-project",
                "A test project",
                List.of("java", "spring"),
                List.of("authentication", "rest-api"),
                "monolith",
                false,
                null,
                "A test project summary");

        final BlueprintPlan blueprint = new BlueprintPlan(
                "test-project",
                "Architecture overview",
                List.of(),
                List.of(),
                "IDE rules content",
                "starter notes");

        final StarterSelection starterSelection = new StarterSelection(
                java.util.UUID.randomUUID(),
                "test-project",
                "cursor",
                new StarterCandidate(
                        "spring-boot-postgres-api",
                        "Spring Boot Postgres API",
                        "spring-boot",
                        StarterSourceKind.OFFICIAL_ADAPTER,
                        "spring-initializr",
                        List.of("spring-boot", "postgres"),
                        "Matches spring-boot and postgres for the detected project requirements."),
                List.of(),
                false,
                "");
        when(promptAnalyzerService.analyzePrompt(anyString())).thenReturn(analysis);
        when(starterResolutionService.resolveStarter(eq(analysis), eq("cursor"))).thenReturn(starterSelection);
        when(planningModelPolicyService.resolvePolicy(eq(analysis.planningComplexity()), eq(RESOLVED_HEAVY_MODEL), eq(false)))
                .thenReturn(new PlanningModelPolicy(PlanningMode.MULTI_PASS_EXPANSION, "elevated", true, true,
                        RESOLVED_HEAVY_MODEL.modelKey(), ModelCapability.HIGH, false, "none"));
        when(blueprintPlanningService.planBlueprint(eq(analysis), eq("cursor"), eq(RESOLVED_HEAVY_MODEL), eq(starterSelection), any()))
                .thenReturn(new BlueprintPlanningResult(
                        BLUEPRINT_PLAN_COMPATIBILITY_ADAPTER.toCanonicalBlueprint(blueprint),
                        true,
                        java.util.Map.of(),
                        java.util.Map.of()));

        final List<GenerationStage> stages = new ArrayList<>();
        final OrchestrationResult result = orchestrationManager.orchestrate(
                "Build a REST API with Spring Boot",
                "cursor",
                RESOLVED_HEAVY_MODEL,
                new GenerationCancellationToken(java.util.UUID.randomUUID()),
                (stage, message) -> stages.add(stage));

        assertThat(result.canonicalBlueprint().projectName()).isEqualTo(blueprint.projectName());
        assertThat(result.starterSelection()).isEqualTo(starterSelection);
        assertThat(stages).containsExactly(
                GenerationStage.ANALYZING,
                GenerationStage.MATCHING_STARTER,
                GenerationStage.PREPARING_REVIEW,
                GenerationStage.PLANNING_BLUEPRINT);
    }

    @Test
    @DisplayName("Error path: blueprint generation failure should bubble up")
    void shouldPropagateBlueprintGenerationFailure() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "test-project",
                "A test project",
                List.of("java"),
                List.of("api"),
                "monolith",
                false,
                null,
                "A test project summary");

        when(promptAnalyzerService.analyzePrompt(anyString())).thenReturn(analysis);
        when(planningModelPolicyService.resolvePolicy(eq(analysis.planningComplexity()), eq(RESOLVED_HEAVY_MODEL), eq(true)))
                .thenReturn(new PlanningModelPolicy(PlanningMode.MULTI_PASS_EXPANSION, "high", true, true,
                        RESOLVED_HEAVY_MODEL.modelKey(), ModelCapability.HIGH, false, "none"));
        
        final StarterSelection starterSelection = new StarterSelection(
                java.util.UUID.randomUUID(),
                "test-project",
                "cursor",
                null,
                List.of(),
                true,
                "No confident approved starter matched the requested stack.");
        when(starterResolutionService.resolveStarter(eq(analysis), eq("cursor"))).thenReturn(starterSelection);
        when(blueprintPlanningService.planBlueprint(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        assertThatThrownBy(() -> orchestrationManager.orchestrate(
                "Build an API",
                "cursor",
                RESOLVED_HEAVY_MODEL,
                new GenerationCancellationToken(java.util.UUID.randomUUID()),
                (stage, message) -> {
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM service unavailable");
    }

    @Test
    @DisplayName("Cancellation: should stop before blueprint generation when token is cancelled")
    void shouldStopWhenCancellationIsRequested() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "test-project",
                "A test project",
                List.of("java"),
                List.of("api"),
                "monolith",
                false,
                null,
                "A test project summary");
        final GenerationCancellationToken token = new GenerationCancellationToken(java.util.UUID.randomUUID());

        when(promptAnalyzerService.analyzePrompt(anyString())).thenAnswer(invocation -> {
            token.cancel(GenerationFailureReason.USER_CANCELLED);
            return analysis;
        });
        assertThatThrownBy(() -> orchestrationManager.orchestrate(
                "Build an API",
                "cursor",
                RESOLVED_HEAVY_MODEL,
                token,
                (stage, message) -> {
                }))
                .isInstanceOf(com.arslanca.orchestrai.core.exception.type.ProjectGenerationCancelledException.class);
    }

    @Test
    @DisplayName("Cancellation: should stop after starter resolution before blueprint generation")
    void shouldStopAfterStarterResolutionWhenCancellationIsRequested() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "test-project",
                "api",
                List.of("spring-boot", "postgres"),
                List.of("auth"),
                "moderate",
                false,
                null,
                "A test project summary");
        final GenerationCancellationToken token = new GenerationCancellationToken(java.util.UUID.randomUUID());
        final StarterSelection starterSelection = new StarterSelection(
                java.util.UUID.randomUUID(),
                "test-project",
                "cursor",
                null,
                List.of(),
                true,
                "No confident approved starter matched the requested stack.");

        when(promptAnalyzerService.analyzePrompt(anyString())).thenReturn(analysis);
        when(starterResolutionService.resolveStarter(eq(analysis), eq("cursor"))).thenAnswer(invocation -> {
            token.cancel(GenerationFailureReason.USER_CANCELLED);
            return starterSelection;
        });
        assertThatThrownBy(() -> orchestrationManager.orchestrate(
                "Build an API",
                "cursor",
                RESOLVED_HEAVY_MODEL,
                token,
                (stage, message) -> {
                }))
                .isInstanceOf(com.arslanca.orchestrai.core.exception.type.ProjectGenerationCancelledException.class);

        verify(blueprintPlanningService, never()).planBlueprint(eq(analysis), eq("cursor"), eq(RESOLVED_HEAVY_MODEL), eq(starterSelection), any());
    }

    @Test
    @DisplayName("Prepared path: should skip analysis and starter matching and directly generate blueprint")
    void shouldOrchestratePreparedSuccessfully() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "prepared-project",
                "A prepared project",
                List.of("java"),
                List.of("api"),
                "monolith",
                false,
                null,
                "A test project summary");
        final StarterSelection starterSelection = new StarterSelection(
                java.util.UUID.randomUUID(),
                "prepared-project",
                "cursor",
                null,
                List.of(),
                true,
                "No confident approved starter matched the requested stack.");
        final BlueprintPlan blueprint = new BlueprintPlan(
                "prepared-project",
                "Architecture overview",
                List.of(),
                List.of(),
                "IDE rules content",
                "starter notes");

        when(planningModelPolicyService.resolvePolicy(eq(analysis.planningComplexity()), eq(RESOLVED_HEAVY_MODEL), eq(true)))
                .thenReturn(new PlanningModelPolicy(PlanningMode.MULTI_PASS_EXPANSION, "high", true, true,
                        RESOLVED_HEAVY_MODEL.modelKey(), ModelCapability.HIGH, false, "none"));
        when(blueprintPlanningService.planBlueprint(eq(analysis), eq("cursor"), eq(RESOLVED_HEAVY_MODEL), eq(starterSelection), any(), any(GenerationUsageContext.class)))
                .thenReturn(new BlueprintPlanningResult(
                        BLUEPRINT_PLAN_COMPATIBILITY_ADAPTER.toCanonicalBlueprint(blueprint),
                        true,
                        java.util.Map.of(),
                        java.util.Map.of()));

        final List<GenerationStage> stages = new ArrayList<>();
        final OrchestrationResult result = orchestrationManager.orchestratePrepared(
                analysis,
                "cursor",
                RESOLVED_HEAVY_MODEL,
                starterSelection,
                new GenerationCancellationToken(java.util.UUID.randomUUID()),
                (stage, message) -> stages.add(stage));

        assertThat(result.canonicalBlueprint().projectName()).isEqualTo(blueprint.projectName());
        assertThat(result.starterSelection()).isEqualTo(starterSelection);
        assertThat(stages).containsExactly(
                GenerationStage.PREPARING_REVIEW,
                GenerationStage.PLANNING_BLUEPRINT);
        verify(promptAnalyzerService, never()).analyzePrompt(anyString());
        verify(starterResolutionService, never()).resolveStarter(org.mockito.ArgumentMatchers.any(), anyString());
    }
}
