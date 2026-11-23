package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.arslanca.orchestrai.business.abstracts.BlueprintPlanningService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.abstracts.StarterSelectionResolver;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
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
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectPlanningPreviewManagerFailureTest {

    @Mock private ProjectGenerationPreparationRepository preparationRepository;
    @Mock private ProjectPlanningPreviewRepository planningPreviewRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private StarterSelectionResolver starterSelectionResolver;
    @Mock private HeavyModelResolutionService heavyModelResolutionService;
    @Mock private ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;
    @Mock private PlanningModelPolicyService planningModelPolicyService;
    @Mock private BlueprintPlanningService blueprintPlanningService;
    @Mock private TransactionTemplate transactionTemplate;
    private BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter;
    private PreviewPayloadCodec previewPayloadCodec;
    private PreparationSnapshotCodec preparationSnapshotCodec;
    private PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec;

    private ObjectMapper objectMapper;
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
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void shouldMarkPreviewAsFailedWhenBlueprintPlanningFails() throws Exception {
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final StructuredAnalysis analysis = new StructuredAnalysis("P", "S", java.util.List.of(), java.util.List.of(), "C", false, "", "", java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), true, true, false, "C", "M", java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), "", "", java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), StructuredAnalysis.CURRENT_CONTRACT_VERSION);
        final StarterSelection starterSelection = new StarterSelection(UUID.randomUUID(), "P", "cursor", new StarterCandidate("k", "n", "t", StarterSourceKind.REPO_PACK, "s", java.util.List.of(), "r"), java.util.List.of(), false, "");
        
        Constructor<ProjectGenerationPreparationEntity> constructor = ProjectGenerationPreparationEntity.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        final ProjectGenerationPreparationEntity preparation = constructor.newInstance();
        
        Field idField = ProjectGenerationPreparationEntity.class.getDeclaredField("id");
        idField.setAccessible(true); idField.set(preparation, preparationId);
        Field uIdField = ProjectGenerationPreparationEntity.class.getDeclaredField("userId");
        uIdField.setAccessible(true); uIdField.set(preparation, userId);
        Field statusField = ProjectGenerationPreparationEntity.class.getDeclaredField("status");
        statusField.setAccessible(true); statusField.set(preparation, ProjectGenerationPreparationStatus.PREPARED);
        Field ideTargetField = ProjectGenerationPreparationEntity.class.getDeclaredField("ideTarget");
        ideTargetField.setAccessible(true); ideTargetField.set(preparation, "cursor");
        Field sAnalysisField = ProjectGenerationPreparationEntity.class.getDeclaredField("structuredAnalysis");
        sAnalysisField.setAccessible(true); sAnalysisField.set(preparation, objectMapper.convertValue(analysis, new TypeReference<>() {}));
        Field sStarterField = ProjectGenerationPreparationEntity.class.getDeclaredField("starterSelection");
        sStarterField.setAccessible(true); sStarterField.set(preparation, objectMapper.convertValue(starterSelection, new TypeReference<>() {}));

        final ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel("g", "g", "g", com.arslanca.orchestrai.core.ai.provider.CredentialSource.PLATFORM, "p", "g", "G", false, false, null, null, null, java.util.List.of(), ModelCapability.HIGH);
        final PlanningModelPolicy policy = new PlanningModelPolicy(PlanningMode.MULTI_PASS_EXPANSION, "e", true, true, "g", ModelCapability.HIGH, false, "n");

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(preparationRepository.findByIdAndUserIdForUpdate(preparationId, userId)).thenReturn(Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(any(), any())).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(eq(userId), eq(null), eq(null))).thenReturn(resolvedHeavyModel);
        when(planningModelPolicyService.resolvePolicy(any(), any(), eq(false))).thenReturn(policy);
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId)).thenReturn(java.util.List.of());
        
        ProjectPlanningPreviewEntity draftEntity = ProjectPlanningPreviewEntity.createDraft(preparationId, "k", PlanningMode.MULTI_PASS_EXPANSION, "cache-key", PlanningPreview.CURRENT_CONTRACT_VERSION);
        when(planningPreviewRepository.save(any(ProjectPlanningPreviewEntity.class))).thenReturn(draftEntity);
        when(planningPreviewRepository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(draftEntity));

        when(blueprintPlanningService.planBlueprint(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("LLM Timeout"));

        assertThatThrownBy(() -> manager.generatePreview(userId, new GenerationPreviewRequest(preparationId, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("LLM Timeout");

        verify(planningPreviewRepository, org.mockito.Mockito.times(2)).save(any());
        verify(planningPreviewRepository, atLeastOnce()).save(argThat(preview -> preview.getPreviewStatus() == PlanningPreviewStatus.FAILED));
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
}
