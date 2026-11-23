package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.governance.NoOpPlanningTelemetryService;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class BlueprintGeneratorManagerTest {

  @Mock
  private ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;

  @Mock
  private HeavyProviderAdapter heavyProviderAdapter;

  @Mock
  private ChatModel heavyChatModel;

  @Mock
  private DeterministicPlanningPolicyService deterministicPlanningPolicyService;

  @Mock
  private AiUsageCaptureService aiUsageCaptureService;

  @Mock
  private PlanningTelemetryService planningTelemetryService;

  private BlueprintGeneratorManager blueprintGeneratorManager;

  @BeforeEach
  void setUp() {
    blueprintGeneratorManager = new BlueprintGeneratorManager(
        resolvedHeavyModelRuntimeBindingManager,
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
        new NoOpPlanningTelemetryService(),
        deterministicPlanningPolicyService);
    when(deterministicPlanningPolicyService.deriveProfile(any())).thenReturn(planningProfile());
  }

  @Test
  void shouldMergeDeterministicStarterNotesWithAiStarterNotes() {
    stubHeavyModelResponse("""
        {
          "projectName": "starter-project",
          "architectureOverview": "overview",
          "phases": [],
          "agents": [],
          "ideRules": "rules",
          "starterNotes": "Use the generated starter modules carefully."
        }
        """);

    final CanonicalBlueprint blueprintPlan = blueprintGeneratorManager.generateBlueprint(
        analysis(),
        "cursor",
        resolvedHeavyModel(),
        starterSelection(false));

    assertThat(blueprintPlan.starterNotes()).contains("Recommended starter: nextjs-saas-shell");
    assertThat(blueprintPlan.starterNotes()).contains("AI Planning Notes");
    assertThat(blueprintPlan.starterNotes()).contains("Use the generated starter modules carefully.");
  }

  @Test
  void shouldReturnDeterministicStarterNotesWhenAiNotesAreBlank() {
    stubHeavyModelResponse("""
        {
          "projectName": "starter-project",
          "architectureOverview": "overview",
          "phases": [],
          "agents": [],
          "ideRules": "rules",
          "starterNotes": ""
        }
        """);

    final CanonicalBlueprint blueprintPlan = blueprintGeneratorManager.generateBlueprint(
        analysis(),
        "cursor",
        resolvedHeavyModel(),
        starterSelection(false));

    assertThat(blueprintPlan.starterNotes()).contains("Recommended starter: nextjs-saas-shell");
    assertThat(blueprintPlan.starterNotes()).doesNotContain("AI Planning Notes");
  }

  @Test
  void shouldIncludeDocsOnlyNotesWhenFallbackSelectionIsUsed() {
    stubHeavyModelResponse("""
        {
          "projectName": "starter-project",
          "architectureOverview": "overview",
          "phases": [],
          "agents": [],
          "ideRules": "rules",
          "starterNotes": ""
        }
        """);

    final CanonicalBlueprint blueprintPlan = blueprintGeneratorManager.generateBlueprint(
        analysis(),
        "cursor",
        resolvedHeavyModel(),
        starterSelection(true));

    assertThat(blueprintPlan.starterNotes()).contains("Mode: docs-only");
    assertThat(blueprintPlan.starterNotes()).contains("No confident approved starter matched the requested stack.");
    assertThat(blueprintPlan.starterNotes()).doesNotContain("Recommended starter:");
  }

  @Test
  void shouldRepairRawNewlinesInsideJsonStringFields() {
    stubHeavyModelResponse("""
        {
          "projectName": "starter-project",
          "architectureOverview": "overview",
          "phases": [],
          "agents": [],
          "ideRules": "# Rules
        - Keep phases isolated
        - Escape JSON-sensitive content",
          "starterNotes": ""
        }
        """);

    final CanonicalBlueprint blueprintPlan = blueprintGeneratorManager.generateBlueprint(
        analysis(),
        "cursor",
        resolvedHeavyModel(),
        starterSelection(false));

    assertThat(blueprintPlan.ideRules()).contains("# Rules");
    assertThat(blueprintPlan.ideRules()).contains("- Keep phases isolated");
    assertThat(blueprintPlan.ideRules()).contains("- Escape JSON-sensitive content");
  }

  @Test
  void shouldPreserveRichSinglePassFieldsAndFillDeterministicPlanningSignals() {
    stubHeavyModelResponse("""
        {
          "projectName": "starter-project",
          "executiveSummary": "summary",
          "architectureOverview": "overview",
          "systemContext": "system context",
          "majorArchitectureDecisions": ["use modular monolith"],
          "workstreams": [
            {
              "title": "Backend Core",
              "goal": "Ship backend",
              "whyItExists": "Needed for auth and data",
              "includedScope": ["api", "auth"],
              "excludedScope": [],
              "dependencies": [],
              "suggestedAgents": ["backend-engineer"],
              "phases": []
            }
          ],
          "nonFunctionalRequirements": ["availability"],
          "crossCuttingConcerns": ["security"],
          "glossary": ["tenant"],
          "ideRules": "rules",
          "starterNotes": ""
        }
        """);

    final CanonicalBlueprint blueprintPlan = blueprintGeneratorManager.generateBlueprint(
        analysis(),
        "cursor",
        resolvedHeavyModel(),
        starterSelection(false));

    assertThat(blueprintPlan.executiveSummary()).isEqualTo("summary");
    assertThat(blueprintPlan.systemContext()).isEqualTo("system context");
    assertThat(blueprintPlan.majorArchitectureDecisions()).contains("use modular monolith");
    assertThat(blueprintPlan.workstreams()).hasSize(1);
    assertThat(blueprintPlan.nonFunctionalRequirements()).contains("availability");
    assertThat(blueprintPlan.crossCuttingConcerns()).contains("security");
    assertThat(blueprintPlan.glossary()).contains("tenant");
    assertThat(blueprintPlan.assumptions()).isEmpty();
    assertThat(blueprintPlan.unknowns()).isEmpty();
    assertThat(blueprintPlan.phases()).isEmpty();
    assertThat(blueprintPlan.contractVersion()).isEqualTo(CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
  }

  @Test
  void shouldInjectDeterministicPlanningContextIntoSinglePassPrompt() {
    stubHeavyModelResponse("""
        {
          "projectName": "starter-project",
          "architectureOverview": "overview",
          "phases": [],
          "agents": [],
          "ideRules": "rules",
          "starterNotes": ""
        }
        """);

    blueprintGeneratorManager.generateBlueprint(
        promptTransportAnalysis(),
        "cursor",
        resolvedHeavyModel(),
        starterSelection(false));

    final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(heavyChatModel).call(promptCaptor.capture());
    final String promptText = promptCaptor.getValue().getInstructions().get(1).getText();

    assertThat(promptText)
        .contains("Effective Complexity: ENTERPRISE")
        .contains("Hard Signals: multi-tenancy, self-hosted-deployment, async-processing")
        .contains("Mandatory Coverage Families: backend-foundation, async-processing, identity-and-access")
        .contains("Expected Workstreams: 4-7")
        .contains("Expected Phases: 8-12")
        .contains("Expected Agents: 5-10")
        .contains("Scale Expectations: 10k orgs")
        .contains("Latency Expectations: sub-second dashboard reads")
        .contains("Deployment Model: self-hosted")
        .contains("Runtime Topology: frontend=true, backend=true, worker=true")
        .contains("Treat the deterministic starter resolution as authoritative")
        .contains("Recommended Starter: nextjs-saas-shell")
        .contains("Reason: Matches nextjs and typescript for the detected project requirements.");
  }

  @Test
  void shouldCaptureGenerationUsageMetadataAndProviderTotalsForSinglePassBlueprint() {
    final BlueprintGeneratorManager captureAwareManager = new BlueprintGeneratorManager(
        resolvedHeavyModelRuntimeBindingManager,
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
        planningTelemetryService,
        deterministicPlanningPolicyService,
        aiUsageCaptureService);
    final UUID connectionId = UUID.randomUUID();
    final UUID projectId = UUID.randomUUID();
    final UUID preparationId = UUID.randomUUID();
    final UUID previewId = UUID.randomUUID();
    final GenerationUsageContext usageContext = new GenerationUsageContext(
        UUID.randomUUID(),
        projectId,
        preparationId,
        previewId,
        "credit-res-123",
        "corr-123");

    when(resolvedHeavyModelRuntimeBindingManager.bind(any())).thenAnswer(invocation ->
        new com.arslanca.orchestrai.business.runtime.ResolvedHeavyModelRuntimeBinding(
            invocation.getArgument(0),
            heavyProviderAdapter,
            new ProviderCredential(
                "openai",
                "test-key",
                CredentialSource.BYOK,
                "user",
                connectionId,
                AiConnectionType.OPENAI_BYOK)));
    when(heavyProviderAdapter.buildHeavyChatModel(any(), any())).thenReturn(heavyChatModel);
    when(heavyChatModel.call(any(Prompt.class))).thenReturn(chatResponseWithUsage("""
        {
          "projectName": "starter-project",
          "architectureOverview": "overview",
          "phases": [],
          "agents": [],
          "ideRules": "rules",
          "starterNotes": ""
        }
        """, 120, 36, 156));

    captureAwareManager.generateBlueprint(
        analysis(),
        "cursor",
        new ResolvedHeavyModel(
            "openai:gpt-4.1",
            "openai",
            "gpt-4.1",
            CredentialSource.BYOK,
            "user",
            "gpt-4.1",
            "GPT-4.1",
            false,
            false,
            null,
            null,
            null,
            List.of(),
            com.arslanca.orchestrai.business.dtos.ai.ModelCapability.HIGH,
            connectionId,
            com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass.USER_SINGLE_ROUTE,
            AiConnectionType.OPENAI_BYOK),
        starterSelection(false),
        usageContext);

    final ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
    verify(aiUsageCaptureService).capture(
        eq(usageContext.userId()),
        eq("corr-123"),
        eq(AiUsageSurface.GENERATION),
        eq(AiUsageEventStatus.COMPLETED),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        metadataCaptor.capture());
    assertThat(metadataCaptor.getValue())
        .containsEntry("projectId", projectId)
        .containsEntry("preparationId", preparationId)
        .containsEntry("previewId", previewId)
        .containsEntry("creditReservationReference", "credit-res-123")
        .containsEntry("correlationId", "corr-123")
        .containsEntry("operation", "single_pass_blueprint");

    verify(planningTelemetryService).recordPassExecution(
        eq("single_pass_blueprint"),
        eq(PlanningComplexity.MODERATE),
        any(),
        eq(156),
        anyInt(),
        anyInt(),
        anyLong());
  }

  @Test
  void shouldPreserveProviderFailureTypeWhenSinglePassCallFails() {
    final BlueprintGeneratorManager captureAwareManager = new BlueprintGeneratorManager(
        resolvedHeavyModelRuntimeBindingManager,
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
        planningTelemetryService,
        deterministicPlanningPolicyService,
        aiUsageCaptureService);
    final RuntimeException providerFailure = new RuntimeException("provider unavailable");
    final GenerationUsageContext usageContext = new GenerationUsageContext(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "credit-res-789",
        "corr-789");

    when(resolvedHeavyModelRuntimeBindingManager.bind(any())).thenAnswer(invocation ->
        new com.arslanca.orchestrai.business.runtime.ResolvedHeavyModelRuntimeBinding(
            invocation.getArgument(0),
            heavyProviderAdapter,
            new ProviderCredential("google", "test-key", CredentialSource.PLATFORM, "platform")));
    when(heavyProviderAdapter.buildHeavyChatModel(any(), any())).thenReturn(heavyChatModel);
    when(heavyChatModel.call(any(Prompt.class))).thenThrow(providerFailure);

    assertThatThrownBy(() -> captureAwareManager.generateBlueprint(
        analysis(),
        "cursor",
        resolvedHeavyModel(),
        starterSelection(false),
        usageContext))
        .isSameAs(providerFailure);

    verify(aiUsageCaptureService).capture(
        eq(usageContext.userId()),
        eq("corr-789"),
        eq(AiUsageSurface.GENERATION),
        eq(AiUsageEventStatus.FAILED),
        any(),
        any(),
        eq(null),
        eq(null),
        any(),
        any(),
        any());
  }

  private void stubHeavyModelResponse(final String jsonResponse) {
    when(resolvedHeavyModelRuntimeBindingManager.bind(any())).thenAnswer(invocation ->
        new com.arslanca.orchestrai.business.runtime.ResolvedHeavyModelRuntimeBinding(
            invocation.getArgument(0),
            heavyProviderAdapter,
            new ProviderCredential("google", "test-key", CredentialSource.PLATFORM, "platform")));
    when(heavyProviderAdapter.buildHeavyChatModel(any(), any())).thenReturn(heavyChatModel);
    when(heavyChatModel.call(any(Prompt.class))).thenReturn(
        new ChatResponse(List.of(new Generation(new AssistantMessage(jsonResponse)))));
  }

  private ChatResponse chatResponseWithUsage(
      final String jsonResponse,
      final int promptTokens,
      final int completionTokens,
      final int totalTokens) {
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage(jsonResponse))),
        ChatResponseMetadata.builder()
            .id("provider-request-1")
            .model("gpt-4.1")
            .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
            .build());
  }

  private StructuredAnalysis analysis() {
    return new StructuredAnalysis(
        "starter-project",
        "saas",
        List.of("nextjs", "typescript"),
        List.of("billing", "dashboard"),
        "moderate",
        false,
        null,
        "A SaaS dashboard project");
  }

  private StructuredAnalysis promptTransportAnalysis() {
    return new StructuredAnalysis(
        "starter-project",
        "saas analytics platform",
        List.of("nextjs", "spring-boot", "postgres"),
        List.of("billing", "dashboard", "async ingestion"),
        "complex",
        false,
        null,
        "A self-hosted multi-tenant analytics SaaS.",
        List.of("org admin"),
        List.of("serve multiple customer organizations"),
        List.of("review dashboards", "ingest events"),
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
        List.of("starter remains approved"),
        List.of("retention policy TBD"),
        List.of("ingestion throughput?"),
        List.of("customer deployment topology"),
        List.of("native mobile"),
        List.of("multi-tenant", "async-processing", "dashboard-and-api"),
        StructuredAnalysis.CURRENT_CONTRACT_VERSION);
  }

  private DeterministicPlanningProfile planningProfile() {
    return new DeterministicPlanningProfile(
        PlanningComplexity.ENTERPRISE,
        List.of(
            PlanningHardSignal.MULTI_TENANCY,
            PlanningHardSignal.SELF_HOSTED_DEPLOYMENT,
            PlanningHardSignal.ASYNC_PROCESSING),
        new PlanningShapeEnvelope(
            4,
            7,
            8,
            12,
            5,
            10,
            List.of(
                PlanningCoverageFamily.BACKEND_FOUNDATION,
                PlanningCoverageFamily.ASYNC_PROCESSING,
                PlanningCoverageFamily.IDENTITY_AND_ACCESS),
            "4-7 workstreams | 8-12 phases | 5-10 agents",
            "Enterprise scope benefits from a stronger planner model."),
        List.of("Enterprise signals detected"));
  }

  private ResolvedHeavyModel resolvedHeavyModel() {
    return new ResolvedHeavyModel(
        "google:gemini-3-pro",
        "google",
        "gemini-3-pro",
        CredentialSource.PLATFORM,
        "platform",
        "gemini-3-pro",
        "Gemini 3 Pro");
  }

  private StarterSelection starterSelection(final boolean docsOnlyFallback) {
    if (docsOnlyFallback) {
      return new StarterSelection(
          UUID.randomUUID(),
          "starter-project",
          "cursor",
          null,
          List.of(),
          true,
          "No confident approved starter matched the requested stack.");
    }

    return new StarterSelection(
        UUID.randomUUID(),
        "starter-project",
        "cursor",
        new StarterCandidate(
            "nextjs-saas-shell",
            "Next.js SaaS Shell",
            "nextjs",
            StarterSourceKind.REPO_PACK,
            "nextjs-saas-shell",
            List.of("nextjs", "react", "typescript"),
            "Matches nextjs and typescript for the detected project requirements."),
        List.of(new StarterCandidate(
            "nextjs-dashboard-starter",
            "Next.js Dashboard Starter",
            "nextjs",
            StarterSourceKind.REPO_PACK,
            "nextjs-dashboard-starter",
            List.of("nextjs", "react", "typescript"),
            "Alternative approved starter matching nextjs, react, typescript.")),
        false,
        "");
  }
}
