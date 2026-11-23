package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.arslanca.orchestrai.business.abstracts.ArtifactAssemblyService;
import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.CreditTransactionService;
import com.arslanca.orchestrai.business.abstracts.DistributedSemaphoreService;
import com.arslanca.orchestrai.business.abstracts.GenerationFailureCompensationService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.OrchestrationService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationCancellationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationFinalizationService;
import com.arslanca.orchestrai.business.abstracts.ScaffoldMaterializationService;
import com.arslanca.orchestrai.business.abstracts.StarterSelectionResolver;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.generation.runtime.PreparedGenerationLauncher;
import com.arslanca.orchestrai.business.generation.runtime.PreparedGenerationPipelineRunner;
import com.arslanca.orchestrai.business.generation.runtime.PreparedGenerationStartContextResolver;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.blueprint.PreviewPayloadCodec;
import com.arslanca.orchestrai.business.planning.lifecycle.ChatFreshnessPolicy;
import com.arslanca.orchestrai.business.planning.lifecycle.LegacyBridgePolicy;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.planning.lifecycle.PreparationStateGuard;
import com.arslanca.orchestrai.business.planning.lifecycle.PreviewOwnershipValidator;
import com.arslanca.orchestrai.business.planning.lifecycle.PreviewReusePolicy;
import com.arslanca.orchestrai.business.planning.lifecycle.StartAuthorizationPolicy;
import com.arslanca.orchestrai.business.planning.preparation.PreparationSnapshotCodec;
import com.arslanca.orchestrai.business.planning.governance.DefaultPlanningBudgetPolicy;
import com.arslanca.orchestrai.business.planning.governance.DefaultPlanningGovernanceService;
import com.arslanca.orchestrai.business.planning.governance.DefaultPolicyVersionFingerprintResolver;
import com.arslanca.orchestrai.business.planning.governance.DefaultPreviewBillingPolicy;
import com.arslanca.orchestrai.business.planning.governance.DefaultPreviewCachePolicy;
import com.arslanca.orchestrai.business.planning.governance.MicrometerPlanningTelemetryService;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceContext;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshot;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshotCodec;
import com.arslanca.orchestrai.business.planning.governance.PlanningRolloutPolicy;
import com.arslanca.orchestrai.business.planning.governance.PropertyBackedPlanningRolloutPolicy;
import com.arslanca.orchestrai.business.concretes.cancellation.GenerationCancellationToken;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializationMetadata;
import com.arslanca.orchestrai.business.dtos.projectgeneration.OrchestrationResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;
import com.arslanca.orchestrai.business.dtos.projectgeneration.RunCreatedPayload;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.dtos.sse.SseProgressEvent;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;
import com.arslanca.orchestrai.core.exception.type.PreparationStateException;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.arslanca.orchestrai.entity.enums.IdeTarget;
import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class PreparedProjectGenerationManagerTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectGenerationPreparationRepository preparationRepository;
    @Mock private ProjectPlanningPreviewRepository planningPreviewRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private CreditManagementService creditManagementService;
    @Mock private CreditTransactionService creditTransactionService;
    @Mock private HeavyModelResolutionService heavyModelResolutionService;
    @Mock private HeavyModelCredentialResolver heavyModelCredentialResolver;
    @Mock private ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;
    @Mock private PlanningModelPolicyService planningModelPolicyService;
    @Mock private OrchestrationService orchestrationService;
    @Mock private ProjectStateService projectStateService;
    @Mock private DistributedSemaphoreService distributedSemaphoreService;
    @Mock private GenerationFailureCompensationService generationFailureCompensationService;
    @Mock private ProjectGenerationCancellationService projectGenerationCancellationService;
    @Mock private ProjectGenerationFinalizationService projectGenerationFinalizationService;
    @Mock private StarterSelectionResolver starterSelectionResolver;
    @Mock private ScaffoldMaterializationService scaffoldMaterializationService;
    @Mock private ArtifactAssemblyService artifactAssemblyService;

    private ThreadPoolTaskScheduler taskScheduler;
    private PreparedProjectGenerationManager manager;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter;
    private PreparationSnapshotCodec preparationSnapshotCodec;
    private PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec;

    @BeforeEach
    void setUp() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        blueprintPlanCompatibilityAdapter = new BlueprintPlanCompatibilityAdapter();
        preparationSnapshotCodec = new PreparationSnapshotCodec(objectMapper);
        planningGovernanceSnapshotCodec = new PlanningGovernanceSnapshotCodec(objectMapper);
        final var applicationTaskExecutor = new SyncTaskExecutor();
        final PlanningLifecycleAuthority lifecycleAuthority = lifecycleAuthority();
        final MicrometerPlanningTelemetryService planningTelemetryService = new MicrometerPlanningTelemetryService(meterRegistry);
        final PlanningGovernanceProperties governanceProperties = new PlanningGovernanceProperties();
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(governanceProperties);
        final DefaultPlanningBudgetPolicy budgetPolicy = new DefaultPlanningBudgetPolicy();
        final DefaultPreviewBillingPolicy billingPolicy = new DefaultPreviewBillingPolicy();
        final DefaultPolicyVersionFingerprintResolver resolver = new DefaultPolicyVersionFingerprintResolver(
                budgetPolicy,
                billingPolicy,
                rolloutPolicy);
        final DefaultPreviewCachePolicy previewCachePolicy = new DefaultPreviewCachePolicy(objectMapper, resolver, governanceProperties);
        manager = new PreparedProjectGenerationManager(
                projectRepository,
                new PreparedGenerationStartContextResolver(
                        preparationRepository,
                        creditManagementService,
                        creditTransactionService,
                        heavyModelResolutionService,
                        resolvedHeavyModelRuntimeBindingManager,
                        planningModelPolicyService,
                        new DeterministicPlanningPolicyManager(),
                        projectStateService,
                        lifecycleAuthority,
                        preparationSnapshotCodec,
                        new DefaultPlanningGovernanceService(
                                budgetPolicy,
                                billingPolicy,
                                previewCachePolicy,
                                planningTelemetryService,
                                rolloutPolicy,
                                resolver),
                        planningGovernanceSnapshotCodec,
                        planningTelemetryService),
                new PreparedGenerationLauncher(
                        applicationTaskExecutor,
                        projectGenerationCancellationService,
                        generationFailureCompensationService,
                        new PreparedGenerationPipelineRunner(
                                orchestrationService,
                                projectStateService,
                                applicationTaskExecutor,
                                taskScheduler,
                                distributedSemaphoreService,
                                generationFailureCompensationService,
                                projectGenerationCancellationService,
                                projectGenerationFinalizationService,
                                lifecycleAuthority,
                                scaffoldMaterializationService,
                                artifactAssemblyService,
                                meterRegistry,
                                blueprintPlanCompatibilityAdapter),
                        meterRegistry),
                projectGenerationCancellationService,
                meterRegistry);
        org.mockito.Mockito.lenient().when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(starterSelectionResolver.resolveSelectedStarter(any(), org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(starterSelectionResolver.resolveSelectedStarter(any(), eq("nextjs-saas-shell")))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(planningModelPolicyService.resolvePolicy(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new PlanningModelPolicy(
                        com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.REDUCED_MULTI_PASS,
                        CapabilityRisk.MODERATE.value(),
                        false,
                        false,
                        "google:gemini-3-pro",
                        ModelCapability.HIGH,
                        false,
                        DegradeStrategy.NONE.value()));
        org.mockito.Mockito.lenient().when(resolvedHeavyModelRuntimeBindingManager.bind(any()))
                .thenAnswer(invocation -> new com.arslanca.orchestrai.business.runtime.ResolvedHeavyModelRuntimeBinding(
                        invocation.getArgument(0),
                        mock(com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter.class),
                        mock(com.arslanca.orchestrai.core.ai.provider.ProviderCredential.class)));
    }

    @Test
    @DisplayName("startGeneration should reject stale chat preparations")
    void shouldRejectStaleChatPreparation() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID chatId = UUID.randomUUID();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, chatId, 4L, starterSelection());
        final ProjectPlanningPreviewEntity acceptedPreview = ProjectPlanningPreviewEntity.createDraft(
                preparationId,
                "nextjs-saas-shell",
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview.CURRENT_CONTRACT_VERSION);
        acceptedPreview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        acceptedPreview.markAccepted();

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(messageRepository.countByChatId(chatId)).thenReturn(5L);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of(acceptedPreview));

        assertThatThrownBy(() -> manager.startGeneration(
                userId,
                new GenerationStartRequest(preparationId, null),
                mock(SseEventPublisher.class)))
                .isInstanceOf(PreparationStateException.class)
                .hasMessageContaining("chat changed");

        assertThat(readPreparationField(preparation, "status")).isEqualTo(ProjectGenerationPreparationStatus.INVALIDATED);
        assertThat(acceptedPreview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        assertThat(meterRegistry.counter(
                "project_generation.start.request",
                "outcome", "rejected",
                "reason", "chat_changed").count()).isEqualTo(1.0d);
        verify(creditManagementService, never()).reserveCredit(any());
    }

    @Test
    @DisplayName("startGeneration should reject when another preview draft is in progress")
    void shouldRejectStartWhilePreviewDraftExists() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, null, null, starterSelection());
        final ProjectPlanningPreviewEntity draftPreview = ProjectPlanningPreviewEntity.createDraft(
                preparationId,
                "nextjs-saas-shell",
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview.CURRENT_CONTRACT_VERSION);

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of(draftPreview));

        assertThatThrownBy(() -> manager.startGeneration(
                userId,
                new GenerationStartRequest(preparationId, null),
                mock(SseEventPublisher.class)))
                .isInstanceOf(PreparationStateException.class)
                .hasMessageContaining("currently being generated");

        verify(creditManagementService, never()).reserveCredit(any());
    }

    @Test
    @DisplayName("startGeneration should fail before side effects when the bound heavy model is unavailable")
    void shouldFailFastWhenBoundHeavyModelIsUnavailable() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(
                preparationId,
                null,
                null,
                starterSelection(),
                new StructuredAnalysis(
                        "Acme Platform",
                        "saas",
                        List.of("nextjs", "postgres"),
                        List.of("auth", "projects"),
                        "medium",
                        false,
                        "",
                        "AI orchestration workspace"),
                Map.of("resolvedHeavyModel", objectMapper.convertValue(defaultResolvedHeavyModel(), MAP_TYPE)));

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of());
        when(resolvedHeavyModelRuntimeBindingManager.bind(any())).thenThrow(
                com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException.serviceUnavailable(
                        "Secilen heavy model provider'i sistemde aktif degil."));

        assertThatThrownBy(() -> manager.startGeneration(
                userId,
                new GenerationStartRequest(preparationId, null),
                mock(SseEventPublisher.class)))
                .isInstanceOf(com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException.class)
                .hasMessageContaining("provider");

        verify(creditManagementService, never()).reserveCredit(userId);
        verify(creditTransactionService, never()).recordProjectGenerationReservation(any(), anyString(), anyString());
        verify(projectStateService, never()).createProjectRecord(any(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("startGeneration should reject invalid starter overrides")
    void shouldRejectInvalidStarterOverride() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, null, null, starterSelection());

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(any(), eq("unsupported-starter")))
                .thenThrow(new IllegalArgumentException("starterKey is not a valid option for this preparation"));

        assertThatThrownBy(() -> manager.startGeneration(
                userId,
                new GenerationStartRequest(preparationId, "unsupported-starter"),
                mock(SseEventPublisher.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("starterKey");

        verify(creditManagementService, never()).reserveCredit(any());
    }

    @Test
    @DisplayName("startGeneration should promote a valid alternative starter")
    void shouldPromoteAlternativeStarter() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID projectId = UUID.randomUUID();
        final StarterSelection starterSelection = starterSelection();
        final StarterSelection promotedStarterSelection = new StarterSelection(
                starterSelection.preparationId(),
                starterSelection.projectName(),
                starterSelection.ideTarget(),
                starterSelection.alternatives().getFirst(),
                List.of(starterSelection.recommendedStarter()),
                false,
                "");
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, null, null, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel(
                "google:gemini-3-pro",
                "google",
                "gemini",
                CredentialSource.PLATFORM,
                "platform",
                "gemini-3-pro",
                "Gemini 3 Pro");
        final ProjectEntity project = mock(ProjectEntity.class);
        final GenerationCancellationToken cancellationToken = new GenerationCancellationToken(projectId);
        final BlueprintPlan blueprintPlan = new BlueprintPlan(
                "Acme Platform",
                "overview",
                List.of(),
                List.of(),
                "rules",
                "notes");
        final MaterializedStarter materializedStarter = new MaterializedStarter(
                "spring-boot-postgres-api",
                StarterSourceKind.REPO_PACK,
                "repo-pack",
                List.of(),
                new MaterializationMetadata(0, 0L, 10L, List.of(), false, ""),
                false);
        final DeliveryResult deliveryResult = new DeliveryResult(
                "Acme Platform",
                "cursor",
                List.of(new FileNode("docs/architect.md", "content")), null);
        final SseEventPublisher publisher = mock(SseEventPublisher.class);

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(projectStateService.createProjectRecord(any(), any(), eq(IdeTarget.CURSOR), eq(resolvedHeavyModel), anyString()))
                .thenReturn(project);
        when(project.getId()).thenReturn(projectId);
        when(projectGenerationCancellationService.openSession(projectId)).thenReturn(cancellationToken);
        when(distributedSemaphoreService.tryAcquire(eq("active_count"), eq(projectId.toString()))).thenReturn(true);
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq("spring-boot-postgres-api"))).thenReturn(promotedStarterSelection);
        when(orchestrationService.orchestratePrepared(any(), eq("cursor"), eq(resolvedHeavyModel), any(), eq(cancellationToken), any(), any(GenerationUsageContext.class)))
                .thenAnswer(invocation -> new OrchestrationResult(canonicalBlueprint(blueprintPlan), invocation.getArgument(3)));
        when(scaffoldMaterializationService.materialize(any(), any(), any())).thenReturn(materializedStarter);
        when(artifactAssemblyService.assembleArtifact(boundaryBlueprint(blueprintPlan), materializedStarter, IdeTarget.CURSOR)).thenReturn(deliveryResult);

        manager.startGeneration(userId, new GenerationStartRequest(preparationId, "spring-boot-postgres-api"), publisher);

        final ArgumentCaptor<StarterSelection> starterSelectionCaptor = ArgumentCaptor.forClass(StarterSelection.class);
        verify(orchestrationService).orchestratePrepared(any(), eq("cursor"), eq(resolvedHeavyModel), starterSelectionCaptor.capture(), eq(cancellationToken), any(), any(GenerationUsageContext.class));
        assertThat(starterSelectionCaptor.getValue().recommendedStarter().key()).isEqualTo("spring-boot-postgres-api");
        assertThat(starterSelectionCaptor.getValue().alternatives()).extracting(StarterCandidate::key)
                .contains("nextjs-saas-shell");
        verify(projectGenerationFinalizationService).finalizeSuccess(eq(projectId), eq(userId), anyString(), eq(deliveryResult), eq(resolvedHeavyModel),
                        any(com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection.class),
                        any(com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter.class),
                        any(),
                        any());

        final ArgumentCaptor<SseProgressEvent> eventCaptor = ArgumentCaptor.forClass(SseProgressEvent.class);
        verify(publisher, atLeastOnce()).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).isNotEmpty();
        assertThat(eventCaptor.getAllValues().getFirst().type()).isEqualTo("RUN_CREATED");
        assertThat(eventCaptor.getAllValues().getFirst().data()).isInstanceOf(RunCreatedPayload.class);

        final RunCreatedPayload runCreatedPayload = (RunCreatedPayload) eventCaptor.getAllValues().getFirst().data();
        assertThat(runCreatedPayload.projectId()).isEqualTo(projectId);
        assertThat(runCreatedPayload.preparationId()).isEqualTo(preparationId);
        assertThat(runCreatedPayload.projectName()).isEqualTo("Acme Platform");
        assertThat(runCreatedPayload.status()).isEqualTo("PENDING");
        assertThat(runCreatedPayload.ideTarget()).isEqualTo("cursor");
        assertThat(meterRegistry.counter(
                "project_generation.start.request",
                "outcome", "accepted",
                "reason", "start_dispatched").count()).isEqualTo(1.0d);
        assertThat(meterRegistry.counter(
                "project_generation.run_created",
                "outcome", "emitted").count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("startGeneration should reject complex preparations without an accepted preview")
    void shouldRejectComplexPreparationWithoutAcceptedPreview() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID projectId = UUID.randomUUID();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(
                preparationId,
                null,
                null,
                starterSelection(),
                new StructuredAnalysis(
                        "Acme Platform",
                        "saas",
                        List.of("nextjs", "postgres"),
                        List.of("auth", "billing"),
                        "complex",
                        false,
                        "",
                        "Complex platform"),
                Map.of(
                        "planningSummary",
                        objectMapper.convertValue(new PlanningSummary(
                                com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity.COMPLEX,
                                List.of("multi-tenant"),
                                "saas | frontend + backend",
                                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                                "elevated",
                                true,
                                true,
                                false,
                                "starter",
                                "google:gemini-3-pro",
                                ModelCapability.HIGH,
                                false,
                                "none",
                                PlanningSummary.CURRENT_CONTRACT_VERSION), MAP_TYPE)));
        final ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel(
                "google:gemini-3-pro",
                "google",
                "gemini",
                CredentialSource.PLATFORM,
                "platform",
                "gemini-3-pro",
                "Gemini 3 Pro");

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));

        assertThatThrownBy(() -> manager.startGeneration(
                userId,
                new GenerationStartRequest(preparationId, null),
                mock(SseEventPublisher.class)))
                .isInstanceOf(com.arslanca.orchestrai.business.planning.lifecycle.LifecycleViolationException.class)
                .hasMessageContaining("accepted planning preview");

        verify(creditManagementService, never()).reserveCredit(userId);
        verify(orchestrationService, never()).orchestratePrepared(any(), eq("cursor"), eq(resolvedHeavyModel), any(), any(), any(), any(GenerationUsageContext.class));
    }

    @Test
    @DisplayName("startGeneration should allow direct generation when planner capability looks light")
    void shouldAllowDirectGenerationWhenPlannerCapabilityLooksLight() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID projectId = UUID.randomUUID();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(
                preparationId,
                null,
                null,
                starterSelection,
                new StructuredAnalysis(
                        "Acme Platform",
                        "saas",
                        List.of("nextjs", "postgres"),
                        List.of("auth", "billing"),
                        "moderate",
                        false,
                        "",
                        "Moderate platform"),
                Map.of());
        final ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel(
                "ollama:deepseek-r1",
                "ollama",
                "deepseek",
                CredentialSource.PLATFORM,
                "platform",
                "deepseek-r1",
                "DeepSeek R1",
                false,
                false,
                null,
                null,
                null,
                List.of("LOCAL"),
                ModelCapability.LOW);

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(), eq(resolvedHeavyModel), eq(false))).thenReturn(new PlanningModelPolicy(
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.REDUCED_MULTI_PASS,
                CapabilityRisk.HIGH.value(),
                true,
                false,
                resolvedHeavyModel.modelKey(),
                resolvedHeavyModel.capability(),
                true,
                DegradeStrategy.STRONGER_MODEL_REQUIRED.value()));
        stubSuccessfulDirectStart(userId, preparationId, projectId, preparation, resolvedHeavyModel);

        manager.startGeneration(
                userId,
                new GenerationStartRequest(preparationId, null),
                mock(SseEventPublisher.class));

        verify(creditManagementService).reserveCredit(userId);
        verify(orchestrationService).orchestratePrepared(any(), eq("cursor"), eq(resolvedHeavyModel), any(), any(), any(), any(GenerationUsageContext.class));
    }

    @Test
    @DisplayName("startGeneration should reuse accepted preview payload without orchestration")
    void shouldReuseAcceptedPreviewPayloadWithoutOrchestration() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final UUID projectId = UUID.randomUUID();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, null, null, starterSelection);
        final ProjectPlanningPreviewEntity previewEntity = ProjectPlanningPreviewEntity.createDraft(
                preparationId,
                "nextjs-saas-shell",
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview.CURRENT_CONTRACT_VERSION);
        writePreviewField(previewEntity, "id", previewId);
        final BlueprintPlan previewBlueprint = new BlueprintPlan(
                "Acme Platform",
                "overview",
                List.of(),
                List.of(),
                "rules",
                "notes");
        previewEntity.markReadyForReview(Map.of(), Map.of(), objectMapper.convertValue(previewBlueprint, MAP_TYPE));
        final PlanningGovernanceSnapshot compatibleSnapshot = compatibleGovernanceSnapshot(preparation, starterSelection, defaultStartPlanningPolicy());
        writePreviewField(previewEntity, "previewCacheKey", compatibleSnapshot.cacheDecision().previewCacheKey());
        writePreviewField(previewEntity, "governanceSnapshotJson", planningGovernanceSnapshotCodec.toPayload(compatibleSnapshot));
        previewEntity.markAccepted();

        final ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel(
                "google:gemini-3-pro",
                "google",
                "gemini",
                CredentialSource.PLATFORM,
                "platform",
                "gemini-3-pro",
                "Gemini 3 Pro");
        final ProjectEntity project = mock(ProjectEntity.class);
        final GenerationCancellationToken cancellationToken = new GenerationCancellationToken(projectId);
        final MaterializedStarter materializedStarter = new MaterializedStarter(
                "nextjs-saas-shell",
                StarterSourceKind.REPO_PACK,
                "repo-pack",
                List.of(),
                new MaterializationMetadata(0, 0L, 10L, List.of(), false, ""),
                false);
        final DeliveryResult deliveryResult = new DeliveryResult(
                "Acme Platform",
                "cursor",
                List.of(new FileNode("docs/architect.md", "content")), null);
        final SseEventPublisher publisher = mock(SseEventPublisher.class);

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(planningPreviewRepository.findById(previewId)).thenReturn(Optional.of(previewEntity));
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(projectStateService.createProjectRecord(any(), any(), eq(IdeTarget.CURSOR), eq(resolvedHeavyModel), anyString()))
                .thenReturn(project);
        when(project.getId()).thenReturn(projectId);
        when(projectGenerationCancellationService.openSession(projectId)).thenReturn(cancellationToken);
        when(distributedSemaphoreService.tryAcquire(eq("active_count"), eq(projectId.toString()))).thenReturn(true);
        when(scaffoldMaterializationService.materialize(any(), any(), any())).thenReturn(materializedStarter);
        when(artifactAssemblyService.assembleArtifact(boundaryBlueprint(previewBlueprint), materializedStarter, IdeTarget.CURSOR)).thenReturn(deliveryResult);

        manager.startGeneration(userId, new GenerationStartRequest(preparationId, null, previewId), publisher);

        verify(orchestrationService, never()).orchestratePrepared(any(), any(), any(), any(), any(), any(), any(GenerationUsageContext.class));
        verify(artifactAssemblyService).assembleArtifact(boundaryBlueprint(previewBlueprint), materializedStarter, IdeTarget.CURSOR);
        assertThat(meterRegistry.counter(
                "planning.start.reuse",
                "complexity", "moderate",
                "effectiveComplexity", "moderate",
                "planningMode", "reduced_multi_pass",
                "rolloutMode", "off",
                "mode", "accepted_preview",
                "billingDisposition", "accepted_preview_reused_generation_billed_once").count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("startGeneration should reject expired accepted previews with governance TTL")
    void shouldRejectExpiredAcceptedPreview() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, null, null, starterSelection);
        final ProjectPlanningPreviewEntity previewEntity = ProjectPlanningPreviewEntity.createDraft(
                preparationId,
                "nextjs-saas-shell",
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview.CURRENT_CONTRACT_VERSION);
        writePreviewField(previewEntity, "id", previewId);
        previewEntity.markReadyForReview(Map.of(), Map.of(), objectMapper.convertValue(
                new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes"),
                MAP_TYPE));
        writePreviewField(previewEntity, "governanceSnapshotJson", Map.of(
                "complexity", "COMPLEX",
                "planningMode", "MULTI_PASS_EXPANSION",
                "billingDecision", Map.of(
                        "acceptedPreviewStartDisposition", "accepted_preview_reused_generation_billed_once",
                        "freshStartDisposition", "fresh_planning_generation_billed_once"),
                "cacheDecision", Map.of(
                        "previewCacheKey", "cache-key",
                        "plannerModelFingerprint", "google:gemini-3-pro|high|remote",
                        "governancePolicyFingerprint", "fingerprint",
                        "reviewReadyReuseTtlSeconds", 10,
                        "acceptedPreviewReuseTtlSeconds", 1,
                        "cacheable", true)));
        previewEntity.markAccepted();
        writePreviewField(previewEntity, "createdAt", OffsetDateTime.now().minusMinutes(5));

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(planningPreviewRepository.findById(previewId)).thenReturn(Optional.of(previewEntity));

        assertThatThrownBy(() -> manager.startGeneration(
                userId,
                new GenerationStartRequest(preparationId, null, previewId),
                mock(SseEventPublisher.class)))
                .isInstanceOf(PreparationStateException.class)
                .hasMessageContaining("expired");
    }

    private ProjectGenerationPreparationEntity preparationEntity(
            final UUID preparationId,
            final UUID chatId,
            final Long chatMessageCount,
            final StarterSelection starterSelection) {
        return preparationEntity(
                preparationId,
                chatId,
                chatMessageCount,
                starterSelection,
                new StructuredAnalysis(
                        "Acme Platform",
                        "saas",
                        List.of("nextjs", "postgres"),
                        List.of("auth", "projects"),
                        "medium",
                        false,
                        "",
                        "AI orchestration workspace"),
                Map.of());
    }

    private ProjectGenerationPreparationEntity preparationEntity(
            final UUID preparationId,
            final UUID chatId,
            final Long chatMessageCount,
            final StarterSelection starterSelection,
            final StructuredAnalysis structuredAnalysis,
            final Map<String, Object> snapshotJson) {
        try {
            final Constructor<ProjectGenerationPreparationEntity> constructor = ProjectGenerationPreparationEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            final ProjectGenerationPreparationEntity entity = constructor.newInstance();
            writePreparationField(entity, "id", preparationId);
            writePreparationField(entity, "userId", UUID.randomUUID());
            writePreparationField(entity, "chatId", chatId);
            writePreparationField(entity, "chatMessageCount", chatMessageCount);
            writePreparationField(entity, "sourceChannel", chatId == null ? "WEB" : "CHAT");
            writePreparationField(entity, "projectName", "Acme Platform");
            writePreparationField(entity, "ideTarget", "cursor");
            writePreparationField(entity, "promptSummary", "Build an AI orchestration workspace");
            writePreparationField(entity, "structuredAnalysis", objectMapper.convertValue(structuredAnalysis, MAP_TYPE));
            writePreparationField(entity, "starterSelection", objectMapper.convertValue(starterSelection, MAP_TYPE));
            writePreparationField(entity, "status", ProjectGenerationPreparationStatus.PREPARED);
            writePreparationField(entity, "snapshotJson", new LinkedHashMap<>(snapshotJson));
            return entity;
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ResolvedHeavyModel defaultResolvedHeavyModel() {
        return new ResolvedHeavyModel(
                "google:gemini-3-pro",
                "google",
                "gemini-3-pro",
                CredentialSource.PLATFORM,
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
    }

    private StarterSelection starterSelection() {
        return new StarterSelection(
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
                        "Best match for the requested stack."),
                List.of(new StarterCandidate(
                        "spring-boot-postgres-api",
                        "Spring Boot Postgres API",
                        "api",
                        StarterSourceKind.REPO_PACK,
                        "repo-pack",
                        List.of("spring", "postgres"),
                        "Alternative approved starter matching backend-heavy delivery.")),
                false,
                "");
    }

    private Object readPreparationField(final ProjectGenerationPreparationEntity entity, final String fieldName) {
        try {
            final Field field = ProjectGenerationPreparationEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(entity);
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void writePreparationField(
            final ProjectGenerationPreparationEntity entity,
            final String fieldName,
            final Object value) {
        try {
            final Field field = ProjectGenerationPreparationEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void writePreviewField(final ProjectPlanningPreviewEntity entity, final String fieldName, final Object value) {
        try {
            final Field field = ProjectPlanningPreviewEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PlanningLifecycleAuthority lifecycleAuthority() {
        final PlanningGovernanceProperties properties = new PlanningGovernanceProperties();
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(properties);
        final DefaultPlanningBudgetPolicy budgetPolicy = new DefaultPlanningBudgetPolicy();
        final DefaultPreviewBillingPolicy billingPolicy = new DefaultPreviewBillingPolicy();
        final DefaultPolicyVersionFingerprintResolver resolver = new DefaultPolicyVersionFingerprintResolver(
                budgetPolicy,
                billingPolicy,
                rolloutPolicy);
        final DefaultPreviewCachePolicy previewCachePolicy = new DefaultPreviewCachePolicy(objectMapper, resolver, properties);
        final MicrometerPlanningTelemetryService telemetryService = new MicrometerPlanningTelemetryService(meterRegistry);
        final PreviewReusePolicy previewReusePolicy = new PreviewReusePolicy(
                planningPreviewRepository,
                new DefaultPlanningGovernanceService(
                        budgetPolicy,
                        billingPolicy,
                        previewCachePolicy,
                        telemetryService,
                        rolloutPolicy,
                        resolver),
                planningGovernanceSnapshotCodec,
                telemetryService);
        final PreviewOwnershipValidator previewOwnershipValidator = new PreviewOwnershipValidator(planningPreviewRepository, preparationRepository);
        final ChatFreshnessPolicy chatFreshnessPolicy = new ChatFreshnessPolicy(
                messageRepository,
                preparationRepository,
                planningPreviewRepository);
        final PreviewPayloadCodec previewPayloadCodec = new PreviewPayloadCodec(objectMapper, blueprintPlanCompatibilityAdapter);
        final StartAuthorizationPolicy startAuthorizationPolicy = new StartAuthorizationPolicy(
                previewOwnershipValidator,
                starterSelectionResolver,
                previewReusePolicy,
                previewPayloadCodec);
        return new PlanningLifecycleAuthority(
                new PreparationStateGuard(),
                chatFreshnessPolicy,
                previewReusePolicy,
                previewOwnershipValidator,
                startAuthorizationPolicy,
                new LegacyBridgePolicy());
    }

    private PlanningGovernanceSnapshot compatibleGovernanceSnapshot(
            final ProjectGenerationPreparationEntity preparation,
            final StarterSelection starterSelection,
            final PlanningModelPolicy planningModelPolicy) {
        final StructuredAnalysis structuredAnalysis = new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "projects"),
                "medium",
                false,
                "",
                "AI orchestration workspace");
        final PlanningGovernanceProperties properties = new PlanningGovernanceProperties();
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(properties);
        final DefaultPlanningBudgetPolicy budgetPolicy = new DefaultPlanningBudgetPolicy();
        final DefaultPreviewBillingPolicy billingPolicy = new DefaultPreviewBillingPolicy();
        final DefaultPolicyVersionFingerprintResolver resolver = new DefaultPolicyVersionFingerprintResolver(
                budgetPolicy,
                billingPolicy,
                rolloutPolicy);
        final DefaultPlanningGovernanceService governanceService = new DefaultPlanningGovernanceService(
                budgetPolicy,
                billingPolicy,
                new DefaultPreviewCachePolicy(objectMapper, resolver, properties),
                new MicrometerPlanningTelemetryService(meterRegistry),
                rolloutPolicy,
                resolver);
        final DeterministicPlanningProfile planningProfile = new DeterministicPlanningPolicyManager().deriveProfile(structuredAnalysis);
        return governanceService.createSnapshot(new PlanningGovernanceContext(
                preparation,
                structuredAnalysis,
                planningProfile,
                starterSelection.recommendedStarter().key(),
                preparation.getIdeTarget(),
                planningModelPolicy,
                PlanningPreview.CURRENT_CONTRACT_VERSION));
    }

    private PlanningModelPolicy defaultStartPlanningPolicy() {
        return new PlanningModelPolicy(
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.REDUCED_MULTI_PASS,
                CapabilityRisk.MODERATE.value(),
                false,
                false,
                "google:gemini-3-pro",
                ModelCapability.HIGH,
                false,
                DegradeStrategy.NONE.value());
    }

    private CanonicalBlueprint canonicalBlueprint(final BlueprintPlan blueprintPlan) {
        return blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(blueprintPlan);
    }

    private BlueprintPlan boundaryBlueprint(final BlueprintPlan blueprintPlan) {
        return blueprintPlanCompatibilityAdapter.toBlueprintPlan(canonicalBlueprint(blueprintPlan));
    }

    private void stubSuccessfulDirectStart(
            final UUID userId,
            final UUID preparationId,
            final UUID projectId,
            final ProjectGenerationPreparationEntity preparation,
            final ResolvedHeavyModel resolvedHeavyModel) {
        final ProjectEntity project = mock(ProjectEntity.class);
        final GenerationCancellationToken cancellationToken = new GenerationCancellationToken(projectId);
        final BlueprintPlan blueprintPlan = new BlueprintPlan(
                "Acme Platform",
                "overview",
                List.of(),
                List.of(),
                "rules",
                "notes");
        final MaterializedStarter materializedStarter = new MaterializedStarter(
                "nextjs-saas-shell",
                StarterSourceKind.REPO_PACK,
                "repo-pack",
                List.of(),
                new MaterializationMetadata(0, 0L, 10L, List.of(), false, ""),
                false);
        final DeliveryResult deliveryResult = new DeliveryResult(
                "Acme Platform",
                "cursor",
                List.of(new FileNode("docs/architect.md", "content")), null);

        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(projectStateService.createProjectRecord(any(), any(), eq(IdeTarget.CURSOR), eq(resolvedHeavyModel), anyString()))
                .thenReturn(project);
        when(project.getId()).thenReturn(projectId);
        when(projectGenerationCancellationService.openSession(projectId)).thenReturn(cancellationToken);
        when(distributedSemaphoreService.tryAcquire(eq("active_count"), eq(projectId.toString()))).thenReturn(true);
        when(orchestrationService.orchestratePrepared(any(), eq("cursor"), eq(resolvedHeavyModel), any(), eq(cancellationToken), any(), any(GenerationUsageContext.class)))
                .thenAnswer(invocation -> new OrchestrationResult(canonicalBlueprint(blueprintPlan), invocation.getArgument(3)));
        when(scaffoldMaterializationService.materialize(any(), any(), any())).thenReturn(materializedStarter);
        when(artifactAssemblyService.assembleArtifact(boundaryBlueprint(blueprintPlan), materializedStarter, IdeTarget.CURSOR)).thenReturn(deliveryResult);
    }
}
