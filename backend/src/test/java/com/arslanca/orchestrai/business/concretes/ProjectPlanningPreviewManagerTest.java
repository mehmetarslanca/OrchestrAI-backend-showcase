package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.arslanca.orchestrai.business.abstracts.BlueprintPlanningService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.abstracts.StarterSelectionResolver;
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
import com.arslanca.orchestrai.business.planning.governance.NoOpPlanningTelemetryService;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshotCodec;
import com.arslanca.orchestrai.business.planning.governance.PlanningRolloutPolicy;
import com.arslanca.orchestrai.business.planning.governance.PropertyBackedPlanningRolloutPolicy;
import com.arslanca.orchestrai.business.planning.preview.PlanningPreviewFinalizer;
import com.arslanca.orchestrai.business.planning.preview.PlanningPreviewProjection;
import com.arslanca.orchestrai.business.planning.preview.PreviewGenerationContextResolver;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlanningResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;
import com.arslanca.orchestrai.core.exception.type.PreparationStateException;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectPlanningPreviewManagerTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Mock
    private ProjectGenerationPreparationRepository preparationRepository;
    @Mock
    private ProjectPlanningPreviewRepository planningPreviewRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private StarterSelectionResolver starterSelectionResolver;
    @Mock
    private HeavyModelResolutionService heavyModelResolutionService;
    @Mock
    private ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;
    @Mock
    private PlanningModelPolicyService planningModelPolicyService;
    @Mock
    private BlueprintPlanningService blueprintPlanningService;
    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private ObjectMapper objectMapper;
    private BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter;
    private PreviewPayloadCodec previewPayloadCodec;
    private PreparationSnapshotCodec preparationSnapshotCodec;
    private PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec;
    private ProjectPlanningPreviewManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        blueprintPlanCompatibilityAdapter = new BlueprintPlanCompatibilityAdapter();
        previewPayloadCodec = new PreviewPayloadCodec(objectMapper, blueprintPlanCompatibilityAdapter);
        preparationSnapshotCodec = new PreparationSnapshotCodec(objectMapper);
        planningGovernanceSnapshotCodec = new PlanningGovernanceSnapshotCodec(objectMapper);
        final PlanningLifecycleAuthority lifecycleAuthority = lifecycleAuthority();
        manager = new ProjectPlanningPreviewManager(
                preparationRepository,
                planningPreviewRepository,
                new PreviewGenerationContextResolver(
                        preparationRepository,
                        starterSelectionResolver,
                        heavyModelResolutionService,
                        resolvedHeavyModelRuntimeBindingManager,
                        planningModelPolicyService,
                        new DeterministicPlanningPolicyManager(),
                        lifecycleAuthority,
                        preparationSnapshotCodec),
                blueprintPlanningService,
                lifecycleAuthority,
                transactionTemplate,
                new PlanningPreviewFinalizer(
                        planningPreviewRepository,
                        previewPayloadCodec,
                        planningGovernanceSnapshotCodec,
                        new NoOpPlanningTelemetryService()),
                new PlanningPreviewProjection(previewPayloadCodec, blueprintPlanCompatibilityAdapter),
                planningGovernanceSnapshotCodec,
                new NoOpPlanningTelemetryService());
                
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void shouldGenerateReadyForReviewPreview() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final ProjectPlanningPreviewEntity[] previewHolder = new ProjectPlanningPreviewEntity[1];
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                resolvedHeavyModel.modelKey(),
                ModelCapability.HIGH,
                false,
                "none");
        final BlueprintPlan blueprintPlan = new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes");

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq(null))).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(PlanningComplexity.class), eq(resolvedHeavyModel), eq(false))).thenReturn(planningModelPolicy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of());
        when(planningPreviewRepository.save(any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> previewHolder[0] = withPreviewId(invocation.getArgument(0), previewId));
        when(planningPreviewRepository.findById(previewId)).thenAnswer(invocation -> Optional.of(previewHolder[0]));
        when(blueprintPlanningService.planBlueprint(eq(analysis), eq("cursor"), eq(resolvedHeavyModel), eq(starterSelection), eq(planningModelPolicy)))
                .thenReturn(new BlueprintPlanningResult(
                        canonicalBlueprint(blueprintPlan),
                        true,
                        Map.of("previewRequired", true, "qualityVerdict", "accept"),
                        Map.of("status", "not_required")));

        final PlanningPreview preview = manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null));

        assertThat(preview.previewId()).isEqualTo(previewId);
        assertThat(preview.previewStatus()).isEqualTo(PlanningPreviewStatus.READY_FOR_REVIEW);
        assertThat(preview.planningMode()).isEqualTo(PlanningMode.MULTI_PASS_EXPANSION);
        assertThat(preview.blueprintPayload()).isEqualTo(boundaryBlueprint(blueprintPlan));
        assertThat(preview.qualitySummary()).containsEntry("previewRequired", true);
        assertThat(preview.repairStatus()).containsEntry("status", "not_required");
    }

    @Test
    void shouldUsePreparationBoundHeavyModelWhenGeneratingPreview() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final ProjectPlanningPreviewEntity[] previewHolder = new ProjectPlanningPreviewEntity[1];
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                resolvedHeavyModel.modelKey(),
                ModelCapability.HIGH,
                false,
                "none");

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq(null))).thenReturn(starterSelection);
        when(planningModelPolicyService.resolvePolicy(any(PlanningComplexity.class), eq(resolvedHeavyModel), eq(false))).thenReturn(planningModelPolicy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of());
        when(planningPreviewRepository.save(any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> previewHolder[0] = withPreviewId(invocation.getArgument(0), previewId));
        when(planningPreviewRepository.findById(previewId)).thenAnswer(invocation -> Optional.of(previewHolder[0]));
        when(blueprintPlanningService.planBlueprint(eq(analysis), eq("cursor"), eq(resolvedHeavyModel), eq(starterSelection), eq(planningModelPolicy)))
                .thenReturn(new BlueprintPlanningResult(
                        canonicalBlueprint(new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes")),
                        true,
                        Map.of("previewRequired", true),
                        Map.of("status", "not_required")));

        final PlanningPreview preview = manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null));

        assertThat(preview.previewId()).isEqualTo(previewId);
        verify(heavyModelResolutionService, never()).resolve(eq(userId), eq(null), eq(null));
    }

    @Test
    void shouldFailBeforePlanningWhenBoundHeavyModelIsUnavailable() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(resolvedHeavyModelRuntimeBindingManager.bind(any())).thenThrow(
                com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException.serviceUnavailable(
                        "Secilen heavy model provider'i sistemde aktif degil."));

        assertThatThrownBy(() -> manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null)))
                .isInstanceOf(com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException.class)
                .hasMessageContaining("provider");

        verify(blueprintPlanningService, never()).planBlueprint(any(), any(), any(), any(), any());
    }

    @Test
    void shouldPersistRejectedPreviewWhenPlanningResultFailsQualityGate() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final ProjectPlanningPreviewEntity[] previewHolder = new ProjectPlanningPreviewEntity[1];
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                resolvedHeavyModel.modelKey(),
                ModelCapability.HIGH,
                false,
                "none");
        final BlueprintPlan blueprintPlan = new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes");

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq(null))).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(PlanningComplexity.class), eq(resolvedHeavyModel), eq(false))).thenReturn(planningModelPolicy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of());
        when(planningPreviewRepository.save(any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> previewHolder[0] = withPreviewId(invocation.getArgument(0), previewId));
        when(planningPreviewRepository.findById(previewId)).thenAnswer(invocation -> Optional.of(previewHolder[0]));
        when(blueprintPlanningService.planBlueprint(eq(analysis), eq("cursor"), eq(resolvedHeavyModel), eq(starterSelection), eq(planningModelPolicy)))
                .thenReturn(new BlueprintPlanningResult(
                        canonicalBlueprint(blueprintPlan),
                        false,
                        Map.of("previewRequired", true, "qualityVerdict", "reject", "qualityPassed", false),
                        Map.of("status", "rejected", "repairAttempts", 2)));

        final PlanningPreview preview = manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null));

        assertThat(preview.previewStatus()).isEqualTo(PlanningPreviewStatus.REJECTED);
        assertThat(preview.qualitySummary()).containsEntry("qualityPassed", false);
        assertThat(preview.repairStatus()).containsEntry("status", "rejected");
    }

    @Test
    void shouldReuseCachedPreviewWhenCacheKeyMatches() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                resolvedHeavyModel.modelKey(),
                ModelCapability.HIGH,
                false,
                "none");
        final BlueprintPlan blueprintPlan = new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes");
        final ProjectPlanningPreviewEntity[] previewHolder = new ProjectPlanningPreviewEntity[1];

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq(null))).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(PlanningComplexity.class), eq(resolvedHeavyModel), eq(false))).thenReturn(planningModelPolicy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId))
                .thenReturn(List.of())
                .thenAnswer(invocation -> previewHolder[0] == null ? List.of() : List.of(previewHolder[0]));
        when(planningPreviewRepository.save(any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> previewHolder[0] = withPreviewId(invocation.getArgument(0), previewId));
        when(planningPreviewRepository.findById(previewId)).thenAnswer(invocation -> Optional.of(previewHolder[0]));
        when(blueprintPlanningService.planBlueprint(eq(analysis), eq("cursor"), eq(resolvedHeavyModel), eq(starterSelection), eq(planningModelPolicy)))
                .thenReturn(new BlueprintPlanningResult(
                        canonicalBlueprint(blueprintPlan),
                        true,
                        Map.of("previewRequired", true),
                        Map.of("status", "not_required")));

        final PlanningPreview initialPreview = manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null));
        final PlanningPreview reusedPreview = manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null));

        assertThat(initialPreview.previewStatus()).isEqualTo(PlanningPreviewStatus.READY_FOR_REVIEW);
        assertThat(reusedPreview.previewId()).isEqualTo(previewId);
        assertThat(reusedPreview.previewStatus()).isEqualTo(PlanningPreviewStatus.READY_FOR_REVIEW);
        verify(blueprintPlanningService, org.mockito.Mockito.times(1)).planBlueprint(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectConcurrentRequestWhenMatchingDraftPreviewExists() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                resolvedHeavyModel.modelKey(),
                ModelCapability.HIGH,
                false,
                "none");

        final ProjectPlanningPreviewEntity previewEntity = withPreviewId(
                ProjectPlanningPreviewEntity.createDraft(preparationId, "nextjs-saas-shell", PlanningMode.MULTI_PASS_EXPANSION, "cache-key", PlanningPreview.CURRENT_CONTRACT_VERSION),
                UUID.randomUUID());

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq(null))).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(PlanningComplexity.class), eq(resolvedHeavyModel), eq(false))).thenReturn(planningModelPolicy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of(previewEntity));

        assertThatThrownBy(() -> manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null)))
                .isInstanceOf(PreparationStateException.class)
                .hasMessageContaining("currently being generated");

        verify(blueprintPlanningService, never()).planBlueprint(any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnInvalidatedPreviewWhenDraftWasSupersededBeforeFinalize() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                resolvedHeavyModel.modelKey(),
                ModelCapability.HIGH,
                false,
                "none");
        final BlueprintPlan blueprintPlan = new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes");

        final ProjectPlanningPreviewEntity invalidatedPreview = withPreviewId(
                ProjectPlanningPreviewEntity.createDraft(
                        preparationId,
                        "nextjs-saas-shell",
                        PlanningMode.MULTI_PASS_EXPANSION,
                        "cache-key",
                        PlanningPreview.CURRENT_CONTRACT_VERSION),
                previewId);
        invalidatedPreview.markInvalidated("superseded_by_accepted_preview");

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq(null))).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(PlanningComplexity.class), eq(resolvedHeavyModel), eq(false))).thenReturn(planningModelPolicy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of());
        when(planningPreviewRepository.save(any(ProjectPlanningPreviewEntity.class))).thenAnswer(invocation -> withPreviewId(invocation.getArgument(0), previewId));
        when(planningPreviewRepository.findById(previewId)).thenReturn(Optional.of(invalidatedPreview));
        when(blueprintPlanningService.planBlueprint(eq(analysis), eq("cursor"), eq(resolvedHeavyModel), eq(starterSelection), eq(planningModelPolicy)))
                .thenReturn(new BlueprintPlanningResult(
                        canonicalBlueprint(blueprintPlan),
                        true,
                        Map.of("previewRequired", true),
                        Map.of("status", "not_required")));

        final PlanningPreview preview = manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null));

        assertThat(preview.previewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        verify(planningPreviewRepository, org.mockito.Mockito.times(1)).save(any(ProjectPlanningPreviewEntity.class));
    }

    @Test
    void shouldAcceptPreviewAndInvalidateSupersededSiblings() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);

        final ProjectPlanningPreviewEntity targetPreview = withPreviewId(
                ProjectPlanningPreviewEntity.createDraft(preparationId, "nextjs-saas-shell", PlanningMode.MULTI_PASS_EXPANSION, "cache-a", PlanningPreview.CURRENT_CONTRACT_VERSION),
                previewId);
        targetPreview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        final ProjectPlanningPreviewEntity siblingPreview = withPreviewId(
                ProjectPlanningPreviewEntity.createDraft(preparationId, "spring-boot-postgres-api", PlanningMode.MULTI_PASS_EXPANSION, "cache-b", PlanningPreview.CURRENT_CONTRACT_VERSION),
                UUID.randomUUID());
        siblingPreview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));

        when(planningPreviewRepository.findById(previewId)).thenReturn(Optional.of(targetPreview));
        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of(targetPreview, siblingPreview));
        when(planningPreviewRepository.save(any(ProjectPlanningPreviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final PlanningPreview preview = manager.acceptPreview(userId, previewId);

        assertThat(preview.previewStatus()).isEqualTo(PlanningPreviewStatus.ACCEPTED);
        assertThat(siblingPreview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        assertThat(siblingPreview.getRepairStatusJson()).containsEntry("status", "invalidated");
    }

    @Test
    void shouldAllowRetryAfterRejectedPreviewWithoutInvalidatingAcceptedHistory() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final UUID previewId = UUID.randomUUID();
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                resolvedHeavyModel.modelKey(),
                ModelCapability.HIGH,
                false,
                "none");
        final ProjectPlanningPreviewEntity[] previewHolder = new ProjectPlanningPreviewEntity[1];

        final ProjectPlanningPreviewEntity acceptedPreview = withPreviewId(
                ProjectPlanningPreviewEntity.createDraft(
                        preparationId,
                        "legacy-starter",
                        PlanningMode.MULTI_PASS_EXPANSION,
                        "legacy-cache",
                        PlanningPreview.CURRENT_CONTRACT_VERSION),
                UUID.randomUUID());
        acceptedPreview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        acceptedPreview.markAccepted();

        final ProjectPlanningPreviewEntity rejectedPreview = withPreviewId(
                ProjectPlanningPreviewEntity.createDraft(
                        preparationId,
                        "nextjs-saas-shell",
                        PlanningMode.MULTI_PASS_EXPANSION,
                        "stale-cache",
                        PlanningPreview.CURRENT_CONTRACT_VERSION),
                UUID.randomUUID());
        rejectedPreview.markRejected(Map.of(), Map.of("status", "rejected"), Map.of());

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq(null))).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(PlanningComplexity.class), eq(resolvedHeavyModel), eq(false))).thenReturn(planningModelPolicy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of(rejectedPreview, acceptedPreview));
        when(planningPreviewRepository.save(any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> previewHolder[0] = withPreviewId(invocation.getArgument(0), previewId));
        when(planningPreviewRepository.findById(previewId)).thenAnswer(invocation -> Optional.of(previewHolder[0]));
        when(blueprintPlanningService.planBlueprint(eq(analysis), eq("cursor"), eq(resolvedHeavyModel), eq(starterSelection), eq(planningModelPolicy)))
                .thenReturn(new BlueprintPlanningResult(
                        canonicalBlueprint(new BlueprintPlan("Acme Platform", "overview", List.of(), List.of(), "rules", "notes")),
                        true,
                        Map.of("previewRequired", true),
                        Map.of("status", "not_required")));

        final PlanningPreview preview = manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null));

        assertThat(preview.previewStatus()).isEqualTo(PlanningPreviewStatus.READY_FOR_REVIEW);
        assertThat(acceptedPreview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.ACCEPTED);
    }

    @Test
    void shouldRejectPreviewGenerationWhenAnotherDraftAlreadyExists() {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final StructuredAnalysis analysis = complexAnalysis();
        final StarterSelection starterSelection = starterSelection();
        final ProjectGenerationPreparationEntity preparation = preparationEntity(preparationId, analysis, starterSelection);
        final ResolvedHeavyModel resolvedHeavyModel = resolvedHeavyModel();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                resolvedHeavyModel.modelKey(),
                ModelCapability.HIGH,
                false,
                "none");

        final ProjectPlanningPreviewEntity draftPreview = withPreviewId(
                ProjectPlanningPreviewEntity.createDraft(
                        preparationId,
                        "other-starter",
                        PlanningMode.MULTI_PASS_EXPANSION,
                        "different-cache",
                        PlanningPreview.CURRENT_CONTRACT_VERSION),
                UUID.randomUUID());

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(eq(starterSelection), eq(null))).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(PlanningComplexity.class), eq(resolvedHeavyModel), eq(false))).thenReturn(planningModelPolicy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(List.of(draftPreview));

        assertThatThrownBy(() -> manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null)))
                .isInstanceOf(PreparationStateException.class)
                .hasMessageContaining("currently being generated");

        verify(blueprintPlanningService, never()).planBlueprint(any(), any(), any(), any(), any());
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
        final NoOpPlanningTelemetryService telemetryService = new NoOpPlanningTelemetryService();
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

    private ProjectGenerationPreparationEntity preparationEntity(
            final UUID preparationId,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection) {
        try {
            final var constructor = ProjectGenerationPreparationEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            final ProjectGenerationPreparationEntity entity = constructor.newInstance();
            writePreparationField(entity, "id", preparationId);
            writePreparationField(entity, "userId", UUID.randomUUID());
            writePreparationField(entity, "sourceChannel", "WEB");
            writePreparationField(entity, "projectName", analysis.projectName());
            writePreparationField(entity, "ideTarget", "cursor");
            writePreparationField(entity, "promptSummary", analysis.summary());
            writePreparationField(entity, "structuredAnalysis", objectMapper.convertValue(analysis, MAP_TYPE));
            writePreparationField(entity, "starterSelection", objectMapper.convertValue(starterSelection, MAP_TYPE));
            writePreparationField(entity, "snapshotJson", Map.of(
                    "resolvedHeavyModel",
                    objectMapper.convertValue(resolvedHeavyModel(), MAP_TYPE)));
            writePreparationField(entity, "status", ProjectGenerationPreparationStatus.PREPARED);
            return entity;
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private StructuredAnalysis complexAnalysis() {
        return new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "billing"),
                "complex",
                false,
                "",
                "Complex AI orchestration platform",
                List.of("admin"),
                List.of("launch B2B product"),
                List.of("signup"),
                List.of("web"),
                true,
                true,
                false,
                "cloud",
                "multi-tenant",
                List.of("postgres"),
                List.of("stripe"),
                List.of("rbac"),
                List.of(),
                "high growth",
                "interactive",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("multi-tenant"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    }

    private StarterSelection starterSelection() {
        return new StarterSelection(
                UUID.randomUUID(),
                "Acme Platform",
                "cursor",
                new StarterCandidate(
                        "nextjs-saas-shell",
                        "Next.js SaaS Shell",
                        "nextjs",
                        StarterSourceKind.REPO_PACK,
                        "repo-pack",
                        List.of("nextjs", "postgres"),
                        "Best match for the requested stack."),
                List.of(new StarterCandidate(
                        "spring-boot-postgres-api",
                        "Spring Boot Postgres API",
                        "spring",
                        StarterSourceKind.REPO_PACK,
                        "repo-pack",
                        List.of("spring", "postgres"),
                        "Alternative backend-first starter.")),
                false,
                "");
    }

    private ResolvedHeavyModel resolvedHeavyModel() {
        return new ResolvedHeavyModel(
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
    }

    private CanonicalBlueprint canonicalBlueprint(final BlueprintPlan blueprintPlan) {
        return blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(blueprintPlan);
    }

    private BlueprintPlan boundaryBlueprint(final BlueprintPlan blueprintPlan) {
        return blueprintPlanCompatibilityAdapter.toBlueprintPlan(canonicalBlueprint(blueprintPlan));
    }

    private ProjectPlanningPreviewEntity withPreviewId(final ProjectPlanningPreviewEntity entity, final UUID previewId) {
        try {
            final Field field = ProjectPlanningPreviewEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, previewId);
            return entity;
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void writePreparationField(final ProjectGenerationPreparationEntity entity, final String fieldName, final Object value) {
        try {
            final Field field = ProjectGenerationPreparationEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
