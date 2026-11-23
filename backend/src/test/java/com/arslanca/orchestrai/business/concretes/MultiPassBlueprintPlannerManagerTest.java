package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.abstracts.PlanningPassValidationService;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.ArchitectureFramingPass;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalPhase;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalWorkstream;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MultiPassBlueprintPlannerManagerTest {

  @Mock
  private ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private PlanningTelemetryService planningTelemetryService;

  @Mock
  private HeavyProviderAdapter heavyProviderAdapter;

  @Mock
  private ProviderCredential providerCredential;

  @Mock
  private ChatModel chatModel;

  @Mock
  private DeterministicPlanningPolicyService deterministicPlanningPolicyService;

  @Mock
  private AiUsageCaptureService aiUsageCaptureService;

  private PlanningPassValidationService planningPassValidationService;

  private MultiPassBlueprintPlannerManager manager;

  @BeforeEach
  void setUp() {
    planningPassValidationService = new PlanningPassValidationManager();
    manager = new MultiPassBlueprintPlannerManager(
        resolvedHeavyModelRuntimeBindingManager,
        objectMapper,
        planningTelemetryService,
        deterministicPlanningPolicyService,
        planningPassValidationService);
    lenient().when(deterministicPlanningPolicyService.deriveProfile(any())).thenReturn(planningProfile());
  }

  @Test
  void shouldResolveProviderAdapterThroughHeavyModelResolutionManager() throws Exception {
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
    final ProviderCredential providerCredential = providerCredentialFor(resolvedHeavyModel);

    when(resolvedHeavyModelRuntimeBindingManager.bind(resolvedHeavyModel)).thenReturn(
        new com.arslanca.orchestrai.business.runtime.ResolvedHeavyModelRuntimeBinding(
            resolvedHeavyModel,
            heavyProviderAdapter,
            providerCredential));
    when(heavyProviderAdapter.buildHeavyChatModel(resolvedHeavyModel, providerCredential)).thenReturn(chatModel);

    final Method buildChatModel = MultiPassBlueprintPlannerManager.class
        .getDeclaredMethod("buildChatModel", ResolvedHeavyModel.class);
    buildChatModel.setAccessible(true);

    final Object result = buildChatModel.invoke(manager, resolvedHeavyModel);

    assertThat(result).isSameAs(chatModel);
    verify(resolvedHeavyModelRuntimeBindingManager).bind(resolvedHeavyModel);
  }

  @Test
  void shouldRetryPassWithCompactJsonReemitAfterParseFailure() throws Exception {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final Method callPass = MultiPassBlueprintPlannerManager.class.getDeclaredMethod(
        "callPass",
        PlanningComplexity.class,
        PlannerModelRef.class,
        ChatModel.class,
        String.class,
        String.class,
        Class.class,
        String.class);
    callPass.setAccessible(true);
    final PlannerModelRef plannerModelRef = new PlannerModelRef("ollama:gemma4-e4b", ModelCapability.LOW, true);

    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse("Malformed prefix {\"projectName\":\"Acme\""))
        .thenReturn(chatResponse("""
            {
              "projectName": "Acme",
              "executiveSummary": "summary",
              "architectureOverview": "overview",
              "systemContext": "context",
              "majorArchitectureDecisions": ["decision"],
              "nonFunctionalRequirements": ["nfr"],
              "crossCuttingConcerns": ["security"],
              "glossary": ["term"],
              "workstreams": []
            }
            """));

    final ArchitectureFramingPass result = invokeCallPass(
        retryingManager,
        callPass,
        PlanningComplexity.COMPLEX,
        plannerModelRef,
        chatModel,
        ArchitectureFramingPass.class);

    assertThat(result.projectName()).isEqualTo("Acme");
    verify(chatModel, times(2)).call(any(Prompt.class));

    final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(2)).call(promptCaptor.capture());
    final Prompt retryPrompt = promptCaptor.getAllValues().get(1);
    assertThat(retryPrompt.getInstructions().get(1).getText())
        .contains("The previous response could not be parsed as complete JSON");
  }

  @Test
  void shouldRelaxEnterprisePhaseFloorForReducedOrLowCapabilityPath() throws Exception {
    final Method requiredPhaseFloor = MultiPassBlueprintPlannerManager.class.getDeclaredMethod(
        "requiredPhaseFloor",
        DeterministicPlanningProfile.class,
        int.class,
        PlannerModelRef.class,
        boolean.class);
    requiredPhaseFloor.setAccessible(true);

    final int relaxedFloor = (int) requiredPhaseFloor.invoke(
        manager,
        planningProfile(),
        4,
        new PlannerModelRef("ollama:gemma4-e4b", ModelCapability.LOW, true),
        true);

    assertThat(relaxedFloor).isEqualTo(7);
  }

  @Test
  void shouldTransportDeterministicContextAcrossMultiPassPrompts() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse(validArchitectureFramingJson()))
        .thenReturn(chatResponse(validWorkstreamDecompositionJson()))
        .thenReturn(chatResponse(validCompactPhaseExpansionJson()))
        .thenReturn(chatResponse(validAgentGenerationJson()))
        .thenReturn(chatResponse(validIdeRulesJson()));

    retryingManager.generateBlueprint(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        false);

    final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(5)).call(promptCaptor.capture());
    final List<Prompt> prompts = promptCaptor.getAllValues();

    assertThat(prompts.get(0).getInstructions().get(1).getText())
        .contains("Effective Complexity: ENTERPRISE")
        .contains("Expected Workstreams: 4-7")
        .contains("Recommended Starter: spring-boot-postgres-api")
        .contains("Treat the deterministic starter resolution as authoritative");

    assertThat(prompts.get(1).getInstructions().get(1).getText())
        .contains("Event Ingestion | goal: Capture events | why it exists: It owns intake.")
        .contains("Analytics API | goal: Serve reports | why it exists: It owns analytics queries.");

    assertThat(prompts.get(2).getInstructions().get(1).getText())
        .contains("Expected Phases: 8-12")
        .contains("included scope: intake endpoint")
        .contains("suggested agents: backend-engineer");

    assertThat(prompts.get(3).getInstructions().get(1).getText())
        .contains("Expected Agents: 5-10")
        .contains("suggested agents: backend-engineer");

    assertThat(prompts.get(4).getInstructions().get(1).getText())
        .contains("Expected Workstreams: 4-7")
        .contains("Recommended Starter: spring-boot-postgres-api");
  }

  @Test
  void shouldRecoverPhaseExpansionWithCompactRescueAndFlattenNestedPhases() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse(validArchitectureFramingJson()))
        .thenReturn(chatResponse(validWorkstreamDecompositionJson()))
        .thenReturn(chatResponse("Malformed prefix {\"workstreams\": ["))
        .thenReturn(chatResponse("Still malformed {\"workstreams\": ["))
        .thenReturn(chatResponse(validCompactPhaseExpansionJson()))
        .thenReturn(chatResponse(validAgentGenerationJson()))
        .thenReturn(chatResponse(validIdeRulesJson()));

    final CanonicalBlueprint blueprint = retryingManager.generateBlueprint(
        structuredAnalysis(),
        "antigravity",
        resolvedHeavyModel,
        starterSelection(),
        false);

    assertThat(blueprint.workstreams()).hasSize(2);
    assertThat(blueprint.workstreams().get(0).phases()).extracting(CanonicalPhase::order).containsExactly(1, 2, 3, 4);
    assertThat(blueprint.workstreams().get(1).phases()).extracting(CanonicalPhase::order).containsExactly(5, 6, 7, 8);
    assertThat(blueprint.phases()).extracting(CanonicalPhase::order).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    assertThat(blueprint.phases()).extracting(CanonicalPhase::title)
        .containsExactly(
            "Design ingestion boundaries",
            "Implement ingestion contracts",
            "Add ingestion telemetry",
            "Harden ingestion security",
            "Ship analytics API core",
            "Add aggregate queries",
            "Enforce analytics access control",
            "Validate analytics operations");

    final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(7)).call(promptCaptor.capture());
    final List<Prompt> prompts = promptCaptor.getAllValues();
    assertThat(prompts.get(4).getInstructions().get(1).getText())
        .contains("Recovery path: compact_phase_expansion")
        .contains("Respect the expected phase range from the deterministic planning context")
        .contains("Expected Phases: 8-12")
        .doesNotContain("Use 2-4 phases per workstream");
    assertThat(prompts.get(5).getInstructions().get(1).getText())
        .contains("Phase 1: Design ingestion boundaries")
        .contains("Phase 8: Validate analytics operations");
    verify(planningTelemetryService).recordCompactRescueUsage(
        eq("phase expansion"),
        eq(PlanningComplexity.COMPLEX),
        any(PlannerModelRef.class),
        eq("compact_phase_expansion"),
        eq(true));
  }

  @Test
  void shouldCaptureGenerationUsageForEachRetryAndCompactRescueInvocation() {
    final MultiPassBlueprintPlannerManager captureAwareManager = new MultiPassBlueprintPlannerManager(
        resolvedHeavyModelRuntimeBindingManager,
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
        planningTelemetryService,
        deterministicPlanningPolicyService,
        planningPassValidationService,
        aiUsageCaptureService);
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
    final GenerationUsageContext usageContext = new GenerationUsageContext(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "credit-reservation-456",
        "corr-456");

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponseWithUsage(validArchitectureFramingJson(), 90, 20, 110))
        .thenReturn(chatResponseWithUsage(validWorkstreamDecompositionJson(), 80, 18, 98))
        .thenReturn(chatResponseWithUsage("Malformed prefix {\"workstreams\": [", 70, 12, 82))
        .thenReturn(chatResponseWithUsage("Still malformed {\"workstreams\": [", 72, 14, 86))
        .thenReturn(chatResponseWithUsage(validCompactPhaseExpansionJson(), 95, 25, 120))
        .thenReturn(chatResponseWithUsage(validAgentGenerationJson(), 60, 15, 75))
        .thenReturn(chatResponseWithUsage(validIdeRulesJson(), 55, 10, 65));

    captureAwareManager.generateBlueprint(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        false,
        usageContext);

    final ArgumentCaptor<AiUsageEventStatus> statusCaptor = ArgumentCaptor.forClass(AiUsageEventStatus.class);
    final ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
    verify(aiUsageCaptureService, times(7)).capture(
        eq(usageContext.userId()),
        eq("corr-456"),
        eq(AiUsageSurface.GENERATION),
        statusCaptor.capture(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        metadataCaptor.capture());
    assertThat(statusCaptor.getAllValues())
        .containsExactly(
            AiUsageEventStatus.COMPLETED,
            AiUsageEventStatus.COMPLETED,
            AiUsageEventStatus.FAILED,
            AiUsageEventStatus.FAILED,
            AiUsageEventStatus.COMPLETED,
            AiUsageEventStatus.COMPLETED,
            AiUsageEventStatus.COMPLETED);
    assertThat(metadataCaptor.getAllValues().getFirst())
        .containsEntry("projectId", usageContext.projectId())
        .containsEntry("preparationId", usageContext.preparationId())
        .containsEntry("previewId", usageContext.previewId())
        .containsEntry("creditReservationReference", "credit-reservation-456")
        .containsEntry("correlationId", "corr-456")
        .containsEntry("operation", "multi_pass_blueprint_generation")
        .containsEntry("passName", "architecture framing")
        .containsEntry("passAttempt", 1);
    assertThat(metadataCaptor.getAllValues())
        .extracting(metadata -> metadata.get("passName"))
        .contains(
            "architecture framing",
            "workstream decomposition",
            "phase expansion",
            "phase expansion compact rescue",
            "agent generation",
            "ide rules synthesis");

    final ArgumentCaptor<Integer> tokenCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(planningTelemetryService, times(7)).recordPassExecution(
        any(),
        any(),
        any(),
        tokenCaptor.capture(),
        anyInt(),
        anyInt(),
        anyLong());
    assertThat(tokenCaptor.getAllValues()).containsExactly(110, 98, 82, 86, 120, 75, 65);
  }

  @Test
  void shouldRecoverPhaseExpansionWithCompactRescueAfterSemanticValidationFailure() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse(validArchitectureFramingJson()))
        .thenReturn(chatResponse(validWorkstreamDecompositionJson()))
        .thenReturn(chatResponse(emptyPhaseExpansionJson()))
        .thenReturn(chatResponse(validCompactPhaseExpansionJson()))
        .thenReturn(chatResponse(validAgentGenerationJson()))
        .thenReturn(chatResponse(validIdeRulesJson()));

    final CanonicalBlueprint blueprint = retryingManager.generateBlueprint(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        false);

    assertThat(blueprint.phases()).hasSize(8);
    verify(chatModel, times(6)).call(any(Prompt.class));
    verify(planningTelemetryService).recordCompactRescueUsage(
        eq("phase expansion"),
        eq(PlanningComplexity.COMPLEX),
        any(PlannerModelRef.class),
        eq("compact_phase_expansion"),
        eq(true));
  }

  @Test
  void shouldUseDeterministicSkeletonBlueprintForReducedLowCapabilityLocalPlanner() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    final CanonicalBlueprint blueprint = retryingManager.generateBlueprint(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        true);

    assertThat(blueprint.workstreams()).extracting(CanonicalWorkstream::title)
        .contains(
            "Frontend Experience",
            "Backend Foundation",
            "Async Processing",
            "Identity And Access",
            "Tenancy And Isolation");
    assertThat(blueprint.phases()).hasSizeGreaterThanOrEqualTo(8);
    assertThat(blueprint.agents()).hasSizeGreaterThanOrEqualTo(6);
    assertThat(blueprint.ideRules()).contains("Deterministic Planning Rules");

    final BlueprintQualityGateManager qualityGateManager = new BlueprintQualityGateManager(new DeterministicPlanningPolicyManager());
    final var planningPolicy = new PlanningModelPolicyManager().resolvePolicy(
        PlanningComplexity.ENTERPRISE,
        resolvedHeavyModel,
        false);

    assertThat(qualityGateManager.evaluate(blueprint, structuredAnalysis(), planningPolicy, planningProfile()).passed()).isTrue();
    verifyNoInteractions(chatModel);
    verifyNoInteractions(resolvedHeavyModelRuntimeBindingManager);
  }

  @Test
  void shouldRecoverTargetedPhaseExpansionWithCompactRescue() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse("Malformed prefix {\"workstreams\": ["))
        .thenReturn(chatResponse("Still malformed {\"workstreams\": ["))
        .thenReturn(chatResponse(validCompactTargetedPhaseExpansionJson()))
        .thenReturn(chatResponse(validAgentGenerationJson()))
        .thenReturn(chatResponse(validIdeRulesJson()));

    final CanonicalBlueprint expandedBlueprint = retryingManager.expandBlueprintDepth(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        currentBlueprint());

    assertThat(expandedBlueprint.workstreams()).hasSize(2);
    assertThat(expandedBlueprint.phases()).extracting(CanonicalPhase::order).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    assertThat(expandedBlueprint.phases()).extracting(CanonicalPhase::title)
        .containsExactly(
            "Stabilize ingestion contracts",
            "Deepen ingestion validation",
            "Add ingestion observability",
            "Finalize ingestion hardening",
            "Deepen analytics API coverage",
            "Add analytics query optimization",
            "Strengthen analytics RBAC",
            "Finalize analytics readiness");

    final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(5)).call(promptCaptor.capture());
    final List<Prompt> prompts = promptCaptor.getAllValues();
    assertThat(prompts.get(0).getInstructions().get(1).getText())
        .contains("Effective Complexity: ENTERPRISE")
        .contains("Recommended Starter: spring-boot-postgres-api");
    assertThat(prompts.get(2).getInstructions().get(1).getText())
        .contains("Recovery path: compact_targeted_phase_expansion")
        .contains("Expected Phases: 8-12")
        .doesNotContain("Use 2-4 phases per workstream");
    assertThat(prompts.get(3).getInstructions().get(1).getText())
        .contains("Phase 1: Stabilize ingestion contracts")
        .contains("Phase 8: Finalize analytics readiness");
  }

  @Test
  void shouldBatchPhaseExpansionAndResequenceMergedWorkstreams() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse(validArchitectureFramingJson()))
        .thenReturn(chatResponse(validLargeWorkstreamDecompositionJson()))
        .thenReturn(chatResponse(validBatchPhaseExpansionBatchOneJson()))
        .thenReturn(chatResponse(validBatchPhaseExpansionBatchTwoJson()))
        .thenReturn(chatResponse(validAgentGenerationJson()))
        .thenReturn(chatResponse(validIdeRulesJson()));

    final CanonicalBlueprint blueprint = retryingManager.generateBlueprint(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        false);

    assertThat(blueprint.workstreams()).extracting(CanonicalWorkstream::title)
        .containsExactly("Event Ingestion", "Analytics API", "Admin Dashboard", "Tenant Operations");
    assertThat(blueprint.phases()).extracting(CanonicalPhase::order)
        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);

    final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(6)).call(promptCaptor.capture());
    final List<Prompt> prompts = promptCaptor.getAllValues();
    assertThat(prompts.get(2).getInstructions().get(1).getText())
        .contains("Expansion mode: batched_phase_expansion")
        .contains("Current batch: 1/2")
        .contains("Required phase floor for this pass: 4");
    assertThat(prompts.get(3).getInstructions().get(1).getText())
        .contains("Expansion mode: batched_phase_expansion")
        .contains("Current batch: 2/2")
        .contains("Required phase floor for this pass: 3");
  }

  @Test
  void shouldRecoverWorkstreamDecompositionWhenLlmReturnsEmptyWorkstreams() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse(validArchitectureFramingJson()))
        .thenReturn(chatResponse(emptyWorkstreamDecompositionJson()))
        .thenReturn(chatResponse(validCompactPhaseExpansionJson()))
        .thenReturn(chatResponse(validAgentGenerationJson()))
        .thenReturn(chatResponse(validIdeRulesJson()));

    final CanonicalBlueprint blueprint = retryingManager.generateBlueprint(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        false);

    assertThat(blueprint.workstreams()).extracting(CanonicalWorkstream::title)
        .containsExactly("Event Ingestion", "Analytics API");
    verify(chatModel, times(5)).call(any(Prompt.class));
  }

  @Test
  void shouldRestoreMissingWorkstreamCoverageDuringTargetedRepair() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse(validTargetedRepairMissingWorkstreamJson()))
        .thenReturn(chatResponse(validTargetedWorkstreamRestorationJson()))
        .thenReturn(chatResponse(validAgentGenerationJson()))
        .thenReturn(chatResponse(validIdeRulesJson()));

    final CanonicalBlueprint expandedBlueprint = retryingManager.expandBlueprintDepth(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        currentBlueprintWithThreeWorkstreams());

    assertThat(expandedBlueprint.workstreams()).extracting(CanonicalWorkstream::title)
        .containsExactly("Event Ingestion", "Analytics API", "Tenant Operations");
    assertThat(expandedBlueprint.phases()).extracting(CanonicalPhase::order)
        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);

    final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(4)).call(promptCaptor.capture());
    final List<Prompt> prompts = promptCaptor.getAllValues();
    assertThat(prompts.get(1).getInstructions().get(1).getText())
        .contains("Restoration target workstreams:")
        .contains("Tenant Operations")
        .contains("Repair mode: targeted_workstream_restoration");
  }

  @Test
  void shouldFailWhenCompactRescueFallsBelowRequiredPhaseFloor() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse(validArchitectureFramingJson()))
        .thenReturn(chatResponse(validWorkstreamDecompositionJson()))
        .thenReturn(chatResponse("Malformed prefix {\"workstreams\": ["))
        .thenReturn(chatResponse("Still malformed {\"workstreams\": ["))
        .thenReturn(chatResponse(validUnderFloorCompactPhaseExpansionJson()));

    assertThatThrownBy(() -> retryingManager.generateBlueprint(
        structuredAnalysis(),
        "cursor",
        resolvedHeavyModel,
        starterSelection(),
        false))
            .isInstanceOf(LlmResponseParseException.class)
            .hasMessageContaining("below required floor=5");

    verify(chatModel, times(5)).call(any(Prompt.class));
  }

  @Test
  void shouldPreserveFailureWhenCompactPhaseExpansionRescueAlsoFails() {
    final MultiPassBlueprintPlannerManager retryingManager = managerWithObjectMapper();
    final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();

    stubHeavyModelResolution(resolvedHeavyModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(chatResponse(validArchitectureFramingJson()))
        .thenReturn(chatResponse(validWorkstreamDecompositionJson()))
        .thenReturn(chatResponse("Malformed prefix {\"workstreams\": ["))
        .thenReturn(chatResponse("Still malformed {\"workstreams\": ["))
        .thenReturn(chatResponse("Malformed compact {\"workstreams\": ["))
        .thenReturn(chatResponse("Still malformed compact {\"workstreams\": ["));

    assertThatThrownBy(() -> retryingManager.generateBlueprint(
        structuredAnalysis(),
        "antigravity",
        resolvedHeavyModel,
        starterSelection(),
        false))
            .isInstanceOf(LlmResponseParseException.class)
            .hasMessageContaining("phase expansion");

    verify(chatModel, times(6)).call(any(Prompt.class));
    verify(planningTelemetryService).recordCompactRescueUsage(
        eq("phase expansion"),
        eq(PlanningComplexity.COMPLEX),
        any(PlannerModelRef.class),
        eq("compact_phase_expansion"),
        eq(false));
  }

  private ArchitectureFramingPass invokeCallPass(
      final MultiPassBlueprintPlannerManager retryingManager,
      final Method callPass,
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ChatModel heavyChatModel,
      final Class<ArchitectureFramingPass> responseType) throws Exception {
    try {
      return (ArchitectureFramingPass) callPass.invoke(
          retryingManager,
          planningComplexity,
          plannerModelRef,
          heavyChatModel,
          "system",
          "user",
          responseType,
          "architecture framing");
    } catch (final InvocationTargetException exception) {
      if (exception.getCause() instanceof Exception cause) {
        throw cause;
      }
      throw exception;
    }
  }

  private ChatResponse chatResponse(final String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private ChatResponse chatResponseWithUsage(
      final String text,
      final int promptTokens,
      final int completionTokens,
      final int totalTokens) {
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage(text))),
        ChatResponseMetadata.builder()
            .id("provider-request")
            .model("google:gemini-3-pro")
            .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
            .build());
  }

  private void stubHeavyModelResolution(final ResolvedHeavyModel resolvedHeavyModel) {
    final ProviderCredential providerCredential = providerCredentialFor(resolvedHeavyModel);
    when(resolvedHeavyModelRuntimeBindingManager.bind(resolvedHeavyModel)).thenReturn(
        new com.arslanca.orchestrai.business.runtime.ResolvedHeavyModelRuntimeBinding(
            resolvedHeavyModel,
            heavyProviderAdapter,
            providerCredential));
    when(heavyProviderAdapter.buildHeavyChatModel(resolvedHeavyModel, providerCredential)).thenReturn(chatModel);
  }

  private ProviderCredential providerCredentialFor(final ResolvedHeavyModel resolvedHeavyModel) {
    return new ProviderCredential(
        resolvedHeavyModel.provider(),
        "test-key",
        resolvedHeavyModel.source(),
        resolvedHeavyModel.credentialOwner(),
        resolvedHeavyModel.selectedConnectionId(),
        resolvedHeavyModel.connectionType());
  }

  private MultiPassBlueprintPlannerManager managerWithObjectMapper() {
    return new MultiPassBlueprintPlannerManager(
        resolvedHeavyModelRuntimeBindingManager,
        objectMapper(),
        planningTelemetryService,
        deterministicPlanningPolicyService,
        planningPassValidationService);
  }

  private ObjectMapper objectMapper() {
    return new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  private DeterministicPlanningProfile planningProfile() {
    return new DeterministicPlanningProfile(
        PlanningComplexity.ENTERPRISE,
        List.of(
            PlanningHardSignal.MULTI_TENANCY,
            PlanningHardSignal.SELF_HOSTED_DEPLOYMENT,
            PlanningHardSignal.ASYNC_PROCESSING,
            PlanningHardSignal.DASHBOARD_AND_API),
        new PlanningShapeEnvelope(
            4,
            7,
            8,
            12,
            5,
            10,
            List.of(
                PlanningCoverageFamily.FRONTEND_EXPERIENCE,
                PlanningCoverageFamily.BACKEND_FOUNDATION,
                PlanningCoverageFamily.ASYNC_PROCESSING,
                PlanningCoverageFamily.IDENTITY_AND_ACCESS,
                PlanningCoverageFamily.TENANCY_AND_ISOLATION),
            "4-7 workstreams | 8-12 phases | 5-10 agents",
            "Enterprise scope benefits from a stronger planner model."),
        List.of("Enterprise signals detected"));
  }

  private ResolvedHeavyModel resolvedHeavyModel() {
    return new ResolvedHeavyModel(
        "ollama:gemma4-e4b",
        "ollama",
        "gemma4-e4b",
        com.arslanca.orchestrai.core.ai.provider.CredentialSource.PLATFORM,
        "platform",
        "gemma4-e4b",
        "Gemma 4 E4B",
        false,
        false,
        null,
        null,
        null,
        List.of("LOCAL"),
        ModelCapability.LOW);
  }

  private StructuredAnalysis structuredAnalysis() {
    return new StructuredAnalysis(
        "Acme Analytics",
        "analytics dashboard platform",
        List.of("spring-boot", "postgres", "react"),
        List.of("event ingestion", "reporting api", "admin dashboard"),
        "complex",
        false,
        "",
        "Capture user behavior events, expose analytics APIs, and operate a multi-tenant dashboard.",
        List.of("product analyst", "org admin"),
        List.of("understand user behavior", "serve multiple organizations"),
        List.of("ingest events", "query reports"),
        List.of("web"),
        true,
        true,
        true,
        "self-hosted",
        "multi-tenant",
        List.of("postgres", "object storage"),
        List.of("segment", "stripe"),
        List.of("rbac"),
        List.of("soc2"),
        "10k orgs",
        "sub-second dashboard reads",
        List.of("air-gapped install", "audit logging"),
        List.of("assume basic auth"),
        List.of("retention policy TBD"),
        List.of("event cardinality?"),
        List.of("customer deployment topology"),
        List.of("native mobile"),
        List.of("multi-tenant", "async-processing", "dashboard-and-api"),
        StructuredAnalysis.CURRENT_CONTRACT_VERSION);
  }

  private StarterSelection starterSelection() {
    return new StarterSelection(
        java.util.UUID.randomUUID(),
        "Acme Analytics",
        "cursor",
        new StarterCandidate(
            "spring-boot-postgres-api",
            "Spring Boot Postgres API",
            "spring-boot",
            StarterSourceKind.OFFICIAL_ADAPTER,
            "spring-initializr",
            List.of("spring-boot", "postgres"),
            "Best match for an analytics API backend."),
        List.of(),
        false,
        "");
  }

  private CanonicalBlueprint currentBlueprint() {
    return new CanonicalBlueprint(
        "Acme Analytics",
        "Summary",
        "Architecture overview",
        "System context",
        List.of("Use PostgreSQL"),
        List.of(
            new CanonicalWorkstream(
                "Event Ingestion",
                "Capture events",
                "Owns raw event intake",
                List.of("intake endpoint"),
                List.of(),
                List.of(),
                List.of("backend-engineer"),
                List.of(new CanonicalPhase(
                    1,
                    "Capture raw events",
                    "Accept events safely",
                    "The pipeline needs a stable intake.",
                    List.of("http ingestion"),
                    List.of(),
                    List.of(),
                    List.of("ingestion endpoint"),
                    List.of(),
                    List.of(),
                    "postgres tables",
                    List.of("auth"),
                    "request metrics",
                    "api tests",
                    List.of("events are stored"),
                    List.of("schema drift"),
                    "handoff"))),
            new CanonicalWorkstream(
                "Analytics API",
                "Serve reports",
                "Owns analytics queries",
                List.of("report endpoints"),
                List.of(),
                List.of("Event Ingestion"),
                List.of("backend-engineer"),
                List.of(new CanonicalPhase(
                    2,
                    "Serve analytics reports",
                    "Expose reports safely",
                    "Consumers need reporting endpoints.",
                    List.of("report api"),
                    List.of(),
                    List.of("Capture raw events"),
                    List.of("analytics endpoint"),
                    List.of(),
                    List.of(),
                    "read models",
                    List.of("rbac"),
                    "query metrics",
                    "api tests",
                    List.of("reports return data"),
                    List.of("slow queries"),
                    "handoff")))),
        List.of("availability"),
        List.of("security"),
        List.of(),
        List.of("auth assumed"),
        List.of("retention TBD"),
        List.of(),
        List.of(),
        "IDE rules",
        "Starter notes",
        CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
  }

  private CanonicalBlueprint currentBlueprintWithThreeWorkstreams() {
    return new CanonicalBlueprint(
        "Acme Analytics",
        "Summary",
        "Architecture overview",
        "System context",
        List.of("Use PostgreSQL"),
        List.of(
            new CanonicalWorkstream(
                "Event Ingestion",
                "Capture events",
                "Owns raw event intake",
                List.of("intake endpoint"),
                List.of(),
                List.of(),
                List.of("backend-engineer"),
                List.of(new CanonicalPhase(
                    1,
                    "Capture raw events",
                    "Accept events safely",
                    "The pipeline needs a stable intake.",
                    List.of("http ingestion"),
                    List.of(),
                    List.of(),
                    List.of("ingestion endpoint"),
                    List.of(),
                    List.of(),
                    "postgres tables",
                    List.of("auth"),
                    "request metrics",
                    "api tests",
                    List.of("events are stored"),
                    List.of("schema drift"),
                    "handoff"))),
            new CanonicalWorkstream(
                "Analytics API",
                "Serve reports",
                "Owns analytics queries",
                List.of("report endpoints"),
                List.of(),
                List.of("Event Ingestion"),
                List.of("backend-engineer"),
                List.of(new CanonicalPhase(
                    2,
                    "Serve analytics reports",
                    "Expose reports safely",
                    "Consumers need reporting endpoints.",
                    List.of("report api"),
                    List.of(),
                    List.of("Capture raw events"),
                    List.of("analytics endpoint"),
                    List.of(),
                    List.of(),
                    "read models",
                    List.of("rbac"),
                    "query metrics",
                    "api tests",
                    List.of("reports return data"),
                    List.of("slow queries"),
                    "handoff"))),
            new CanonicalWorkstream(
                "Tenant Operations",
                "Operate tenant lifecycle",
                "Owns tenant provisioning and controls",
                List.of("tenant provisioning"),
                List.of(),
                List.of("Analytics API"),
                List.of("backend-engineer"),
                List.of(new CanonicalPhase(
                    3,
                    "Provision tenant baseline",
                    "Create baseline tenant controls",
                    "Tenant isolation needs a stable baseline.",
                    List.of("tenant bootstrap"),
                    List.of(),
                    List.of("Serve analytics reports"),
                    List.of("tenant bootstrap flow"),
                    List.of(),
                    List.of(),
                    "tenant metadata",
                    List.of("rbac"),
                    "tenant audit metrics",
                    "api tests",
                    List.of("tenant baseline is provisioned"),
                    List.of("tenant drift"),
                    "handoff")))),
        List.of("availability"),
        List.of("security"),
        List.of(),
        List.of("auth assumed"),
        List.of("retention TBD"),
        List.of(),
        List.of(),
        "IDE rules",
        "Starter notes",
        CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
  }

  private String validArchitectureFramingJson() {
    return """
        {
          "projectName": "Acme Analytics",
          "executiveSummary": "summary",
          "architectureOverview": "overview",
          "systemContext": "context",
          "majorArchitectureDecisions": ["event-driven ingestion"],
          "nonFunctionalRequirements": ["availability"],
          "crossCuttingConcerns": ["security"],
          "glossary": ["event"],
          "workstreams": [
            {"title": "Event Ingestion", "goal": "Capture events", "whyItExists": "It owns intake."},
            {"title": "Analytics API", "goal": "Serve reports", "whyItExists": "It owns analytics queries."}
          ]
        }
        """;
  }

  private String validWorkstreamDecompositionJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Event Ingestion",
              "goal": "Capture events",
              "whyItExists": "It owns intake.",
              "includedScope": ["intake endpoint"],
              "excludedScope": ["dashboard"],
              "dependencies": [],
              "suggestedAgents": ["backend-engineer"],
              "phases": []
            },
            {
              "title": "Analytics API",
              "goal": "Serve reports",
              "whyItExists": "It owns analytics queries.",
              "includedScope": ["report endpoint"],
              "excludedScope": ["visualization"],
              "dependencies": ["Event Ingestion"],
              "suggestedAgents": ["backend-engineer"],
              "phases": []
            }
          ]
        }
        """;
  }

  private String validCompactPhaseExpansionJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Event Ingestion",
              "goal": "Capture events",
              "whyItExists": "It owns intake.",
              "includedScope": ["intake endpoint"],
              "excludedScope": ["dashboard"],
              "dependencies": [],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Design ingestion boundaries",
                  "objective": "Define stable ingestion contracts.",
                  "whyThisPhaseExists": "Later implementation depends on stable contracts.",
                  "scopeIn": ["event schema"],
                  "scopeOut": ["dashboard ui"],
                  "prerequisites": [],
                  "deliverables": ["schema contract"],
                  "architectureDecisions": ["canonical event envelope"],
                  "contracts": ["ingestion api"],
                  "dataImpact": "new raw event table",
                  "securityConsiderations": ["signed event source"],
                  "observabilityPlan": "Track rejected payload counts.",
                  "testingStrategy": "Schema validation tests.",
                  "acceptanceCriteria": ["invalid events are rejected"],
                  "risks": ["schema drift"],
                  "handoffInstructions": "Proceed with endpoint implementation."
                },
                {
                  "order": 2,
                  "title": "Implement ingestion contracts",
                  "objective": "Ship the approved ingestion API contract.",
                  "whyThisPhaseExists": "Producers need a stable intake surface.",
                  "scopeIn": ["request validation"],
                  "scopeOut": ["analytics queries"],
                  "prerequisites": ["Design ingestion boundaries"],
                  "deliverables": ["ingestion endpoint"],
                  "architectureDecisions": ["validated request envelope"],
                  "contracts": ["ingestion request"],
                  "dataImpact": "validated raw event writes",
                  "securityConsiderations": ["request authentication"],
                  "observabilityPlan": "Track ingestion success and failure counts.",
                  "testingStrategy": "Endpoint contract tests.",
                  "acceptanceCriteria": ["valid events are accepted"],
                  "risks": ["producer mismatch"],
                  "handoffInstructions": "Proceed with ingestion persistence."
                },
                {
                  "order": 3,
                  "title": "Add ingestion telemetry",
                  "objective": "Instrument the ingestion path for operations.",
                  "whyThisPhaseExists": "Operations need early visibility into ingest health.",
                  "scopeIn": ["ingestion metrics"],
                  "scopeOut": ["dashboard widgets"],
                  "prerequisites": ["Implement ingestion contracts"],
                  "deliverables": ["ingestion dashboards"],
                  "architectureDecisions": ["request outcome metrics"],
                  "contracts": ["telemetry events"],
                  "dataImpact": "metrics and audit events",
                  "securityConsiderations": ["redacted telemetry"],
                  "observabilityPlan": "Track latency and rejection rates.",
                  "testingStrategy": "Observability smoke tests.",
                  "acceptanceCriteria": ["ingestion metrics are emitted"],
                  "risks": ["noisy telemetry"],
                  "handoffInstructions": "Proceed with security hardening."
                },
                {
                  "order": 4,
                  "title": "Harden ingestion security",
                  "objective": "Lock down ingestion auth and isolation rules.",
                  "whyThisPhaseExists": "Multi-tenant ingestion must enforce trust boundaries.",
                  "scopeIn": ["authz rules"],
                  "scopeOut": ["reporting endpoints"],
                  "prerequisites": ["Add ingestion telemetry"],
                  "deliverables": ["ingestion auth policy"],
                  "architectureDecisions": ["tenant-aware ingress checks"],
                  "contracts": ["tenant ingestion policy"],
                  "dataImpact": "tenant security metadata",
                  "securityConsiderations": ["tenant isolation"],
                  "observabilityPlan": "Track authorization failures.",
                  "testingStrategy": "Security integration tests.",
                  "acceptanceCriteria": ["cross-tenant writes are blocked"],
                  "risks": ["over-restrictive policy"],
                  "handoffInstructions": "Proceed with downstream analytics delivery."
                }
              ]
            },
            {
              "title": "Analytics API",
              "goal": "Serve reports",
              "whyItExists": "It owns analytics queries.",
              "includedScope": ["report endpoint"],
              "excludedScope": ["visualization"],
              "dependencies": ["Event Ingestion"],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Ship analytics API core",
                  "objective": "Expose the first analytics query surface.",
                  "whyThisPhaseExists": "Consumers need stable reporting endpoints.",
                  "scopeIn": ["report endpoint"],
                  "scopeOut": ["dashboard widgets"],
                  "prerequisites": ["Design ingestion boundaries"],
                  "deliverables": ["analytics api"],
                  "architectureDecisions": ["read-optimized query path"],
                  "contracts": ["report response"],
                  "dataImpact": "read model tables",
                  "securityConsiderations": ["rbac for reports"],
                  "observabilityPlan": "Track query latency.",
                  "testingStrategy": "Endpoint contract tests.",
                  "acceptanceCriteria": ["reports return expected aggregates"],
                  "risks": ["slow queries"],
                  "handoffInstructions": "Proceed with reporting implementation."
                },
                {
                  "order": 2,
                  "title": "Add aggregate queries",
                  "objective": "Implement the core aggregate query set.",
                  "whyThisPhaseExists": "The first API release needs production query coverage.",
                  "scopeIn": ["aggregate reports"],
                  "scopeOut": ["visual dashboards"],
                  "prerequisites": ["Ship analytics API core"],
                  "deliverables": ["aggregate endpoints"],
                  "architectureDecisions": ["precomputed query paths"],
                  "contracts": ["aggregate response"],
                  "dataImpact": "query-ready aggregates",
                  "securityConsiderations": ["scoped query filters"],
                  "observabilityPlan": "Track aggregate query latency.",
                  "testingStrategy": "Query integration tests.",
                  "acceptanceCriteria": ["aggregate queries stay correct"],
                  "risks": ["expensive scans"],
                  "handoffInstructions": "Proceed with access-control hardening."
                },
                {
                  "order": 3,
                  "title": "Enforce analytics access control",
                  "objective": "Apply tenant and RBAC checks across analytics endpoints.",
                  "whyThisPhaseExists": "Shared analytics data must respect tenant boundaries.",
                  "scopeIn": ["rbac policy"],
                  "scopeOut": ["tenant provisioning"],
                  "prerequisites": ["Add aggregate queries"],
                  "deliverables": ["analytics auth policy"],
                  "architectureDecisions": ["tenant-scoped query guards"],
                  "contracts": ["analytics auth rules"],
                  "dataImpact": "tenant access metadata",
                  "securityConsiderations": ["tenant isolation"],
                  "observabilityPlan": "Track authorization denials.",
                  "testingStrategy": "Security access tests.",
                  "acceptanceCriteria": ["unauthorized queries are blocked"],
                  "risks": ["policy drift"],
                  "handoffInstructions": "Proceed with production readiness checks."
                },
                {
                  "order": 4,
                  "title": "Validate analytics operations",
                  "objective": "Finalize analytics readiness for production traffic.",
                  "whyThisPhaseExists": "The API needs operational readiness before rollout.",
                  "scopeIn": ["readiness validation"],
                  "scopeOut": ["ui theming"],
                  "prerequisites": ["Enforce analytics access control"],
                  "deliverables": ["analytics readiness checklist"],
                  "architectureDecisions": ["slow-query guardrails"],
                  "contracts": ["operational runbook"],
                  "dataImpact": "readiness audit records",
                  "securityConsiderations": ["audit logging"],
                  "observabilityPlan": "Track latency, errors, and saturation.",
                  "testingStrategy": "Production-readiness smoke tests.",
                  "acceptanceCriteria": ["analytics api meets readiness gate"],
                  "risks": ["operational regressions"],
                  "handoffInstructions": "Proceed with downstream consumer rollout."
                }
              ]
            }
          ]
        }
        """;
  }

  private String emptyWorkstreamDecompositionJson() {
    return """
        {
          "workstreams": []
        }
        """;
  }

  private String emptyPhaseExpansionJson() {
    return """
        {
          "workstreams": []
        }
        """;
  }

  private String validCompactTargetedPhaseExpansionJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Event Ingestion",
              "goal": "Capture events",
              "whyItExists": "It owns intake.",
              "includedScope": ["intake endpoint"],
              "excludedScope": ["dashboard"],
              "dependencies": [],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Stabilize ingestion contracts",
                  "objective": "Tighten the intake contract.",
                  "whyThisPhaseExists": "The repair path needs clearer boundaries.",
                  "scopeIn": ["request contract"],
                  "scopeOut": ["dashboards"],
                  "prerequisites": [],
                  "deliverables": ["updated intake contract"],
                  "architectureDecisions": ["validate event envelope"],
                  "contracts": ["ingestion request"],
                  "dataImpact": "clarified raw event schema",
                  "securityConsiderations": ["request validation"],
                  "observabilityPlan": "Track contract violations.",
                  "testingStrategy": "Contract validation tests.",
                  "acceptanceCriteria": ["bad payloads are rejected"],
                  "risks": ["producer mismatch"],
                  "handoffInstructions": "Proceed with endpoint hardening."
                },
                {
                  "order": 2,
                  "title": "Deepen ingestion validation",
                  "objective": "Expand request validation across producer cases.",
                  "whyThisPhaseExists": "The repair path needs stronger contract guarantees.",
                  "scopeIn": ["producer validation"],
                  "scopeOut": ["analytics queries"],
                  "prerequisites": ["Stabilize ingestion contracts"],
                  "deliverables": ["validation rules"],
                  "architectureDecisions": ["strict producer validation"],
                  "contracts": ["validation policy"],
                  "dataImpact": "validated request metadata",
                  "securityConsiderations": ["replay protection"],
                  "observabilityPlan": "Track invalid producer payloads.",
                  "testingStrategy": "Producer contract tests.",
                  "acceptanceCriteria": ["invalid producers are rejected"],
                  "risks": ["producer drift"],
                  "handoffInstructions": "Proceed with telemetry repair."
                },
                {
                  "order": 3,
                  "title": "Add ingestion observability",
                  "objective": "Repair missing ingestion telemetry and alerts.",
                  "whyThisPhaseExists": "Operations still need stronger ingest visibility.",
                  "scopeIn": ["ingest metrics"],
                  "scopeOut": ["tenant bootstrap"],
                  "prerequisites": ["Deepen ingestion validation"],
                  "deliverables": ["ingestion alerts"],
                  "architectureDecisions": ["alerted ingest counters"],
                  "contracts": ["alert thresholds"],
                  "dataImpact": "telemetry series",
                  "securityConsiderations": ["redacted metrics"],
                  "observabilityPlan": "Track errors, latency, and drops.",
                  "testingStrategy": "Telemetry smoke tests.",
                  "acceptanceCriteria": ["ingestion alerts fire correctly"],
                  "risks": ["alert fatigue"],
                  "handoffInstructions": "Proceed with final hardening."
                },
                {
                  "order": 4,
                  "title": "Finalize ingestion hardening",
                  "objective": "Close the remaining ingestion security gaps.",
                  "whyThisPhaseExists": "The repaired plan must still satisfy tenant protections.",
                  "scopeIn": ["ingestion authz"],
                  "scopeOut": ["ui routing"],
                  "prerequisites": ["Add ingestion observability"],
                  "deliverables": ["ingestion hardening checklist"],
                  "architectureDecisions": ["tenant-aware authz"],
                  "contracts": ["ingestion security policy"],
                  "dataImpact": "tenant security events",
                  "securityConsiderations": ["tenant isolation"],
                  "observabilityPlan": "Track authz denials.",
                  "testingStrategy": "Security integration tests.",
                  "acceptanceCriteria": ["cross-tenant ingestion is blocked"],
                  "risks": ["policy regressions"],
                  "handoffInstructions": "Proceed with analytics repair."
                }
              ]
            },
            {
              "title": "Analytics API",
              "goal": "Serve reports",
              "whyItExists": "It owns analytics queries.",
              "includedScope": ["report endpoint"],
              "excludedScope": ["visualization"],
              "dependencies": ["Event Ingestion"],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Deepen analytics API coverage",
                  "objective": "Repair the reporting phase depth.",
                  "whyThisPhaseExists": "The original reporting plan was too shallow.",
                  "scopeIn": ["report endpoint"],
                  "scopeOut": ["dashboard widgets"],
                  "prerequisites": ["Stabilize ingestion contracts"],
                  "deliverables": ["expanded analytics api"],
                  "architectureDecisions": ["pre-computed read paths"],
                  "contracts": ["report response"],
                  "dataImpact": "query-ready read model",
                  "securityConsiderations": ["rbac for analytics"],
                  "observabilityPlan": "Track query latency and failures.",
                  "testingStrategy": "Integration tests for report queries.",
                  "acceptanceCriteria": ["reports stay within latency target"],
                  "risks": ["expensive queries"],
                  "handoffInstructions": "Proceed with analytics delivery."
                },
                {
                  "order": 2,
                  "title": "Add analytics query optimization",
                  "objective": "Repair missing query-performance details.",
                  "whyThisPhaseExists": "The repaired analytics plan still needs query tuning.",
                  "scopeIn": ["aggregate query tuning"],
                  "scopeOut": ["dashboard charts"],
                  "prerequisites": ["Deepen analytics API coverage"],
                  "deliverables": ["optimized aggregate queries"],
                  "architectureDecisions": ["precomputed aggregates"],
                  "contracts": ["query performance budget"],
                  "dataImpact": "aggregate read models",
                  "securityConsiderations": ["scoped aggregate filters"],
                  "observabilityPlan": "Track query-plan regressions.",
                  "testingStrategy": "Performance regression tests.",
                  "acceptanceCriteria": ["aggregate queries meet latency target"],
                  "risks": ["stale aggregates"],
                  "handoffInstructions": "Proceed with access-control repair."
                },
                {
                  "order": 3,
                  "title": "Strengthen analytics RBAC",
                  "objective": "Repair missing authorization depth across report APIs.",
                  "whyThisPhaseExists": "Tenant reporting still needs explicit access boundaries.",
                  "scopeIn": ["report authz"],
                  "scopeOut": ["tenant provisioning"],
                  "prerequisites": ["Add analytics query optimization"],
                  "deliverables": ["analytics authz policy"],
                  "architectureDecisions": ["tenant-scoped authz checks"],
                  "contracts": ["analytics access policy"],
                  "dataImpact": "analytics access metadata",
                  "securityConsiderations": ["tenant isolation"],
                  "observabilityPlan": "Track authorization failures.",
                  "testingStrategy": "Security access tests.",
                  "acceptanceCriteria": ["unauthorized report access is blocked"],
                  "risks": ["permission drift"],
                  "handoffInstructions": "Proceed with final readiness repair."
                },
                {
                  "order": 4,
                  "title": "Finalize analytics readiness",
                  "objective": "Close the final analytics readiness gaps.",
                  "whyThisPhaseExists": "The repaired analytics plan needs production safeguards.",
                  "scopeIn": ["analytics readiness"],
                  "scopeOut": ["ui polish"],
                  "prerequisites": ["Strengthen analytics RBAC"],
                  "deliverables": ["analytics readiness runbook"],
                  "architectureDecisions": ["query guardrails"],
                  "contracts": ["operational handoff"],
                  "dataImpact": "readiness audit records",
                  "securityConsiderations": ["audit logging"],
                  "observabilityPlan": "Track readiness signals and failures.",
                  "testingStrategy": "Readiness smoke tests.",
                  "acceptanceCriteria": ["analytics repair passes readiness checks"],
                  "risks": ["readiness regressions"],
                  "handoffInstructions": "Proceed with rollout."
                }
              ]
            }
          ]
        }
        """;
  }

  private String validUnderFloorCompactPhaseExpansionJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Event Ingestion",
              "goal": "Capture events",
              "whyItExists": "It owns intake.",
              "includedScope": ["intake endpoint"],
              "excludedScope": ["dashboard"],
              "dependencies": [],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Design ingestion boundaries",
                  "objective": "Define stable ingestion contracts.",
                  "whyThisPhaseExists": "Later implementation depends on stable contracts.",
                  "scopeIn": ["event schema"],
                  "scopeOut": ["dashboard ui"],
                  "prerequisites": [],
                  "deliverables": ["schema contract"],
                  "architectureDecisions": ["canonical event envelope"],
                  "contracts": ["ingestion api"],
                  "dataImpact": "new raw event table",
                  "securityConsiderations": ["signed event source"],
                  "observabilityPlan": "Track rejected payload counts.",
                  "testingStrategy": "Schema validation tests.",
                  "acceptanceCriteria": ["invalid events are rejected"],
                  "risks": ["schema drift"],
                  "handoffInstructions": "Proceed with endpoint implementation."
                }
              ]
            },
            {
              "title": "Analytics API",
              "goal": "Serve reports",
              "whyItExists": "It owns analytics queries.",
              "includedScope": ["report endpoint"],
              "excludedScope": ["visualization"],
              "dependencies": ["Event Ingestion"],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Ship analytics API core",
                  "objective": "Expose the first analytics query surface.",
                  "whyThisPhaseExists": "Consumers need stable reporting endpoints.",
                  "scopeIn": ["report endpoint"],
                  "scopeOut": ["dashboard widgets"],
                  "prerequisites": ["Design ingestion boundaries"],
                  "deliverables": ["analytics api"],
                  "architectureDecisions": ["read-optimized query path"],
                  "contracts": ["report response"],
                  "dataImpact": "read model tables",
                  "securityConsiderations": ["rbac for reports"],
                  "observabilityPlan": "Track query latency.",
                  "testingStrategy": "Endpoint contract tests.",
                  "acceptanceCriteria": ["reports return expected aggregates"],
                  "risks": ["slow queries"],
                  "handoffInstructions": "Proceed with reporting implementation."
                }
              ]
            }
          ]
        }
        """;
  }

  private String validLargeWorkstreamDecompositionJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Event Ingestion",
              "goal": "Capture events",
              "whyItExists": "It owns intake.",
              "includedScope": ["intake endpoint"],
              "excludedScope": ["dashboard"],
              "dependencies": [],
              "suggestedAgents": ["backend-engineer"],
              "phases": []
            },
            {
              "title": "Analytics API",
              "goal": "Serve reports",
              "whyItExists": "It owns analytics queries.",
              "includedScope": ["report endpoint"],
              "excludedScope": ["visualization"],
              "dependencies": ["Event Ingestion"],
              "suggestedAgents": ["backend-engineer"],
              "phases": []
            },
            {
              "title": "Admin Dashboard",
              "goal": "Serve operational UI",
              "whyItExists": "It owns the internal dashboard surface.",
              "includedScope": ["admin dashboard"],
              "excludedScope": ["mobile app"],
              "dependencies": ["Analytics API"],
              "suggestedAgents": ["frontend-engineer"],
              "phases": []
            },
            {
              "title": "Tenant Operations",
              "goal": "Operate tenant lifecycle",
              "whyItExists": "It owns tenant controls and provisioning.",
              "includedScope": ["tenant provisioning"],
              "excludedScope": ["public marketing site"],
              "dependencies": ["Analytics API"],
              "suggestedAgents": ["backend-engineer"],
              "phases": []
            }
          ]
        }
        """;
  }

  private String validBatchPhaseExpansionBatchOneJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Event Ingestion",
              "goal": "Capture events",
              "whyItExists": "It owns intake.",
              "includedScope": ["intake endpoint"],
              "excludedScope": ["dashboard"],
              "dependencies": [],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Design ingestion contracts",
                  "objective": "Define the ingestion contract.",
                  "whyThisPhaseExists": "Downstream services need a stable contract.",
                  "scopeIn": ["ingestion schema"],
                  "scopeOut": ["dashboard ui"],
                  "prerequisites": [],
                  "deliverables": ["schema contract"],
                  "architectureDecisions": ["canonical event envelope"],
                  "contracts": ["ingestion request"],
                  "dataImpact": "raw event table",
                  "securityConsiderations": ["request auth"],
                  "observabilityPlan": "Track ingest errors.",
                  "testingStrategy": "Contract tests.",
                  "acceptanceCriteria": ["events validate correctly"],
                  "risks": ["schema drift"],
                  "handoffInstructions": "Proceed with ingestion delivery."
                },
                {
                  "order": 2,
                  "title": "Ship ingestion path",
                  "objective": "Deliver the first ingestion path.",
                  "whyThisPhaseExists": "The platform needs working intake.",
                  "scopeIn": ["ingestion endpoint"],
                  "scopeOut": ["analytics queries"],
                  "prerequisites": ["Design ingestion contracts"],
                  "deliverables": ["ingestion endpoint"],
                  "architectureDecisions": ["validated ingest writes"],
                  "contracts": ["producer contract"],
                  "dataImpact": "validated event writes",
                  "securityConsiderations": ["tenant validation"],
                  "observabilityPlan": "Track ingest success rate.",
                  "testingStrategy": "Endpoint tests.",
                  "acceptanceCriteria": ["valid events are stored"],
                  "risks": ["producer errors"],
                  "handoffInstructions": "Proceed with analytics dependencies."
                }
              ]
            },
            {
              "title": "Analytics API",
              "goal": "Serve reports",
              "whyItExists": "It owns analytics queries.",
              "includedScope": ["report endpoint"],
              "excludedScope": ["visualization"],
              "dependencies": ["Event Ingestion"],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Define analytics responses",
                  "objective": "Define the first report contracts.",
                  "whyThisPhaseExists": "Consumers need stable query responses.",
                  "scopeIn": ["report contract"],
                  "scopeOut": ["dashboard widgets"],
                  "prerequisites": ["Ship ingestion path"],
                  "deliverables": ["report contract"],
                  "architectureDecisions": ["read-optimized responses"],
                  "contracts": ["report response"],
                  "dataImpact": "aggregate models",
                  "securityConsiderations": ["rbac filters"],
                  "observabilityPlan": "Track query latency.",
                  "testingStrategy": "Response contract tests.",
                  "acceptanceCriteria": ["responses stay stable"],
                  "risks": ["slow queries"],
                  "handoffInstructions": "Proceed with analytics endpoint delivery."
                },
                {
                  "order": 2,
                  "title": "Ship analytics endpoints",
                  "objective": "Deliver the first analytics endpoints.",
                  "whyThisPhaseExists": "Reporting consumers need working endpoints.",
                  "scopeIn": ["analytics endpoint"],
                  "scopeOut": ["dashboard theming"],
                  "prerequisites": ["Define analytics responses"],
                  "deliverables": ["analytics api"],
                  "architectureDecisions": ["aggregate query path"],
                  "contracts": ["analytics api contract"],
                  "dataImpact": "query-ready aggregates",
                  "securityConsiderations": ["tenant query scopes"],
                  "observabilityPlan": "Track endpoint saturation.",
                  "testingStrategy": "Endpoint integration tests.",
                  "acceptanceCriteria": ["analytics endpoints return aggregates"],
                  "risks": ["aggregate drift"],
                  "handoffInstructions": "Proceed with dashboard and tenant layers."
                }
              ]
            }
          ]
        }
        """;
  }

  private String validBatchPhaseExpansionBatchTwoJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Admin Dashboard",
              "goal": "Serve operational UI",
              "whyItExists": "It owns the internal dashboard surface.",
              "includedScope": ["admin dashboard"],
              "excludedScope": ["mobile app"],
              "dependencies": ["Analytics API"],
              "suggestedAgents": ["frontend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Frame admin dashboard shell",
                  "objective": "Define the dashboard shell.",
                  "whyThisPhaseExists": "Operations need a stable UI entry point.",
                  "scopeIn": ["dashboard shell"],
                  "scopeOut": ["mobile app"],
                  "prerequisites": ["Ship analytics endpoints"],
                  "deliverables": ["dashboard shell"],
                  "architectureDecisions": ["shared admin layout"],
                  "contracts": ["dashboard navigation"],
                  "dataImpact": "none",
                  "securityConsiderations": ["admin auth"],
                  "observabilityPlan": "Track dashboard page errors.",
                  "testingStrategy": "UI smoke tests.",
                  "acceptanceCriteria": ["dashboard shell loads"],
                  "risks": ["layout churn"],
                  "handoffInstructions": "Proceed with dashboard data wiring."
                },
                {
                  "order": 2,
                  "title": "Wire dashboard analytics views",
                  "objective": "Connect dashboard views to analytics APIs.",
                  "whyThisPhaseExists": "Operators need usable analytics screens.",
                  "scopeIn": ["dashboard analytics screens"],
                  "scopeOut": ["public site"],
                  "prerequisites": ["Frame admin dashboard shell"],
                  "deliverables": ["analytics screens"],
                  "architectureDecisions": ["deferred analytics loading"],
                  "contracts": ["dashboard analytics view"],
                  "dataImpact": "none",
                  "securityConsiderations": ["role-based dashboard access"],
                  "observabilityPlan": "Track dashboard load times.",
                  "testingStrategy": "Component integration tests.",
                  "acceptanceCriteria": ["dashboard shows analytics data"],
                  "risks": ["slow data hydration"],
                  "handoffInstructions": "Proceed with tenant controls."
                }
              ]
            },
            {
              "title": "Tenant Operations",
              "goal": "Operate tenant lifecycle",
              "whyItExists": "It owns tenant controls and provisioning.",
              "includedScope": ["tenant provisioning"],
              "excludedScope": ["public marketing site"],
              "dependencies": ["Analytics API"],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Define tenant lifecycle controls",
                  "objective": "Define the tenant lifecycle contract.",
                  "whyThisPhaseExists": "Operations need deterministic tenant controls.",
                  "scopeIn": ["tenant lifecycle"],
                  "scopeOut": ["dashboard charts"],
                  "prerequisites": ["Ship analytics endpoints"],
                  "deliverables": ["tenant lifecycle policy"],
                  "architectureDecisions": ["tenant isolation baseline"],
                  "contracts": ["tenant control contract"],
                  "dataImpact": "tenant metadata",
                  "securityConsiderations": ["tenant isolation"],
                  "observabilityPlan": "Track tenant control events.",
                  "testingStrategy": "Lifecycle contract tests.",
                  "acceptanceCriteria": ["tenant lifecycle is defined"],
                  "risks": ["tenant drift"],
                  "handoffInstructions": "Proceed with tenant provisioning flow."
                },
                {
                  "order": 2,
                  "title": "Ship tenant provisioning flow",
                  "objective": "Deliver the first tenant provisioning flow.",
                  "whyThisPhaseExists": "The platform needs working tenant bootstrap.",
                  "scopeIn": ["tenant bootstrap"],
                  "scopeOut": ["marketing flows"],
                  "prerequisites": ["Define tenant lifecycle controls"],
                  "deliverables": ["tenant provisioning api"],
                  "architectureDecisions": ["provisioning orchestration"],
                  "contracts": ["tenant bootstrap contract"],
                  "dataImpact": "provisioned tenant records",
                  "securityConsiderations": ["provisioning authz"],
                  "observabilityPlan": "Track provisioning success and failure.",
                  "testingStrategy": "Provisioning integration tests.",
                  "acceptanceCriteria": ["tenants provision successfully"],
                  "risks": ["provisioning retries"],
                  "handoffInstructions": "Proceed with rollout readiness."
                }
              ]
            }
          ]
        }
        """;
  }

  private String validTargetedRepairMissingWorkstreamJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Event Ingestion",
              "goal": "Capture events",
              "whyItExists": "It owns intake.",
              "includedScope": ["intake endpoint"],
              "excludedScope": ["dashboard"],
              "dependencies": [],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Stabilize ingestion contracts",
                  "objective": "Tighten the intake contract.",
                  "whyThisPhaseExists": "The repair path needs clearer boundaries.",
                  "scopeIn": ["request contract"],
                  "scopeOut": ["dashboards"],
                  "prerequisites": [],
                  "deliverables": ["updated intake contract"],
                  "architectureDecisions": ["validate event envelope"],
                  "contracts": ["ingestion request"],
                  "dataImpact": "clarified raw event schema",
                  "securityConsiderations": ["request validation"],
                  "observabilityPlan": "Track contract violations.",
                  "testingStrategy": "Contract validation tests.",
                  "acceptanceCriteria": ["bad payloads are rejected"],
                  "risks": ["producer mismatch"],
                  "handoffInstructions": "Proceed with endpoint hardening."
                },
                {
                  "order": 2,
                  "title": "Deepen ingestion validation",
                  "objective": "Expand request validation across producer cases.",
                  "whyThisPhaseExists": "The repair path needs stronger contract guarantees.",
                  "scopeIn": ["producer validation"],
                  "scopeOut": ["analytics queries"],
                  "prerequisites": ["Stabilize ingestion contracts"],
                  "deliverables": ["validation rules"],
                  "architectureDecisions": ["strict producer validation"],
                  "contracts": ["validation policy"],
                  "dataImpact": "validated request metadata",
                  "securityConsiderations": ["replay protection"],
                  "observabilityPlan": "Track invalid producer payloads.",
                  "testingStrategy": "Producer contract tests.",
                  "acceptanceCriteria": ["invalid producers are rejected"],
                  "risks": ["producer drift"],
                  "handoffInstructions": "Proceed with telemetry repair."
                },
                {
                  "order": 3,
                  "title": "Add ingestion observability",
                  "objective": "Repair missing ingestion telemetry and alerts.",
                  "whyThisPhaseExists": "Operations still need stronger ingest visibility.",
                  "scopeIn": ["ingest metrics"],
                  "scopeOut": ["tenant bootstrap"],
                  "prerequisites": ["Deepen ingestion validation"],
                  "deliverables": ["ingestion alerts"],
                  "architectureDecisions": ["alerted ingest counters"],
                  "contracts": ["alert thresholds"],
                  "dataImpact": "telemetry series",
                  "securityConsiderations": ["redacted metrics"],
                  "observabilityPlan": "Track errors, latency, and drops.",
                  "testingStrategy": "Telemetry smoke tests.",
                  "acceptanceCriteria": ["ingestion alerts fire correctly"],
                  "risks": ["alert fatigue"],
                  "handoffInstructions": "Proceed with final hardening."
                }
              ]
            },
            {
              "title": "Analytics API",
              "goal": "Serve reports",
              "whyItExists": "It owns analytics queries.",
              "includedScope": ["report endpoint"],
              "excludedScope": ["visualization"],
              "dependencies": ["Event Ingestion"],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Deepen analytics API coverage",
                  "objective": "Repair the reporting phase depth.",
                  "whyThisPhaseExists": "The original reporting plan was too shallow.",
                  "scopeIn": ["report endpoint"],
                  "scopeOut": ["dashboard widgets"],
                  "prerequisites": ["Stabilize ingestion contracts"],
                  "deliverables": ["expanded analytics api"],
                  "architectureDecisions": ["pre-computed read paths"],
                  "contracts": ["report response"],
                  "dataImpact": "query-ready read model",
                  "securityConsiderations": ["rbac for analytics"],
                  "observabilityPlan": "Track query latency and failures.",
                  "testingStrategy": "Integration tests for report queries.",
                  "acceptanceCriteria": ["reports stay within latency target"],
                  "risks": ["expensive queries"],
                  "handoffInstructions": "Proceed with analytics delivery."
                },
                {
                  "order": 2,
                  "title": "Add analytics query optimization",
                  "objective": "Repair missing query-performance details.",
                  "whyThisPhaseExists": "The repaired analytics plan still needs query tuning.",
                  "scopeIn": ["aggregate query tuning"],
                  "scopeOut": ["dashboard charts"],
                  "prerequisites": ["Deepen analytics API coverage"],
                  "deliverables": ["optimized aggregate queries"],
                  "architectureDecisions": ["precomputed aggregates"],
                  "contracts": ["query performance budget"],
                  "dataImpact": "aggregate read models",
                  "securityConsiderations": ["scoped aggregate filters"],
                  "observabilityPlan": "Track query-plan regressions.",
                  "testingStrategy": "Performance regression tests.",
                  "acceptanceCriteria": ["aggregate queries meet latency target"],
                  "risks": ["stale aggregates"],
                  "handoffInstructions": "Proceed with access-control repair."
                },
                {
                  "order": 3,
                  "title": "Strengthen analytics RBAC",
                  "objective": "Repair missing authorization depth across report APIs.",
                  "whyThisPhaseExists": "Tenant reporting still needs explicit access boundaries.",
                  "scopeIn": ["report authz"],
                  "scopeOut": ["tenant provisioning"],
                  "prerequisites": ["Add analytics query optimization"],
                  "deliverables": ["analytics authz policy"],
                  "architectureDecisions": ["tenant-scoped authz checks"],
                  "contracts": ["analytics access policy"],
                  "dataImpact": "analytics access metadata",
                  "securityConsiderations": ["tenant isolation"],
                  "observabilityPlan": "Track authorization failures.",
                  "testingStrategy": "Security access tests.",
                  "acceptanceCriteria": ["unauthorized report access is blocked"],
                  "risks": ["permission drift"],
                  "handoffInstructions": "Proceed with final readiness repair."
                }
              ]
            }
          ]
        }
        """;
  }

  private String validTargetedWorkstreamRestorationJson() {
    return """
        {
          "workstreams": [
            {
              "title": "Tenant Operations",
              "goal": "Operate tenant lifecycle",
              "whyItExists": "It owns tenant controls and provisioning.",
              "includedScope": ["tenant provisioning"],
              "excludedScope": ["public marketing site"],
              "dependencies": ["Analytics API"],
              "suggestedAgents": ["backend-engineer"],
              "phases": [
                {
                  "order": 1,
                  "title": "Restore tenant lifecycle controls",
                  "objective": "Re-introduce the tenant control contract.",
                  "whyThisPhaseExists": "The repaired plan lost tenant lifecycle coverage.",
                  "scopeIn": ["tenant lifecycle"],
                  "scopeOut": ["dashboard charts"],
                  "prerequisites": ["Strengthen analytics RBAC"],
                  "deliverables": ["tenant lifecycle policy"],
                  "architectureDecisions": ["tenant isolation baseline"],
                  "contracts": ["tenant control contract"],
                  "dataImpact": "tenant metadata",
                  "securityConsiderations": ["tenant isolation"],
                  "observabilityPlan": "Track tenant control events.",
                  "testingStrategy": "Lifecycle contract tests.",
                  "acceptanceCriteria": ["tenant lifecycle is restored"],
                  "risks": ["tenant drift"],
                  "handoffInstructions": "Proceed with provisioning restoration."
                },
                {
                  "order": 2,
                  "title": "Restore tenant provisioning flow",
                  "objective": "Re-introduce tenant provisioning execution.",
                  "whyThisPhaseExists": "Tenant bootstrap must exist before rollout.",
                  "scopeIn": ["tenant bootstrap"],
                  "scopeOut": ["public marketing site"],
                  "prerequisites": ["Restore tenant lifecycle controls"],
                  "deliverables": ["tenant provisioning api"],
                  "architectureDecisions": ["provisioning orchestration"],
                  "contracts": ["tenant bootstrap contract"],
                  "dataImpact": "provisioned tenant records",
                  "securityConsiderations": ["provisioning authz"],
                  "observabilityPlan": "Track provisioning success and failure.",
                  "testingStrategy": "Provisioning integration tests.",
                  "acceptanceCriteria": ["tenant bootstrap is restored"],
                  "risks": ["provisioning retries"],
                  "handoffInstructions": "Proceed with rollout readiness."
                }
              ]
            }
          ]
        }
        """;
  }

  private String validAgentGenerationJson() {
    return """
        {
          "agents": [
            {
              "role": "Backend Engineer",
              "systemPrompt": "Implement the backend plan.",
              "responsibilities": ["Ship APIs", "Own data contracts"]
            }
          ]
        }
        """;
  }

  private String validIdeRulesJson() {
    return """
        {
          "ideRules": "Follow the staged plan.",
          "starterNotes": "Use the approved starter."
        }
        """;
  }
}
