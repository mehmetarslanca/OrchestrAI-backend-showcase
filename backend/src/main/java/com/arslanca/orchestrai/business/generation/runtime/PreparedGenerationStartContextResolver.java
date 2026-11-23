package com.arslanca.orchestrai.business.generation.runtime;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.CreditTransactionService;
import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.concretes.ProjectStateService;
import com.arslanca.orchestrai.business.concretes.ResolvedHeavyModelRuntimeBindingManager;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PreparedGenerationLaunch;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.RunCreatedPayload;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceContext;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceService;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshot;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshotCodec;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.planning.lifecycle.StartAuthorization;
import com.arslanca.orchestrai.business.planning.preparation.PreparationSnapshotCodec;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.arslanca.orchestrai.entity.enums.IdeTarget;

@Service
public class PreparedGenerationStartContextResolver {

    private final ProjectGenerationPreparationRepository preparationRepository;
    private final CreditManagementService creditManagementService;
    private final CreditTransactionService creditTransactionService;
    private final HeavyModelResolutionService heavyModelResolutionService;
    private final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;
    private final PlanningModelPolicyService planningModelPolicyService;
    private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;
    private final ProjectStateService projectStateService;
    private final PlanningLifecycleAuthority lifecycleAuthority;
    private final PreparationSnapshotCodec preparationSnapshotCodec;
    private final PlanningGovernanceService planningGovernanceService;
    private final PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec;
    private final PlanningTelemetryService planningTelemetryService;

    public PreparedGenerationStartContextResolver(
            final ProjectGenerationPreparationRepository preparationRepository,
            final CreditManagementService creditManagementService,
            final CreditTransactionService creditTransactionService,
            final HeavyModelResolutionService heavyModelResolutionService,
            final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager,
            final PlanningModelPolicyService planningModelPolicyService,
            final DeterministicPlanningPolicyService deterministicPlanningPolicyService,
            final ProjectStateService projectStateService,
            final PlanningLifecycleAuthority lifecycleAuthority,
            final PreparationSnapshotCodec preparationSnapshotCodec,
            final PlanningGovernanceService planningGovernanceService,
            final PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec,
            final PlanningTelemetryService planningTelemetryService) {
        this.preparationRepository = preparationRepository;
        this.creditManagementService = creditManagementService;
        this.creditTransactionService = creditTransactionService;
        this.heavyModelResolutionService = heavyModelResolutionService;
        this.resolvedHeavyModelRuntimeBindingManager = resolvedHeavyModelRuntimeBindingManager;
        this.planningModelPolicyService = planningModelPolicyService;
        this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
        this.projectStateService = projectStateService;
        this.lifecycleAuthority = lifecycleAuthority;
        this.preparationSnapshotCodec = preparationSnapshotCodec;
        this.planningGovernanceService = planningGovernanceService;
        this.planningGovernanceSnapshotCodec = planningGovernanceSnapshotCodec;
        this.planningTelemetryService = planningTelemetryService;
    }

    public PreparedGenerationStartContext resolveStart(
            final UUID userId,
            final GenerationStartRequest request,
            final String heavyModelKeyOverride,
            final CredentialSource heavyModelAuthSourceOverride) {
        final ProjectGenerationPreparationEntity preparation = preparationRepository.findByIdAndUserIdForUpdate(request.preparationId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation preparation not found or access denied"));

        lifecycleAuthority.ensureStartAllowed(preparation);

        final StructuredAnalysis structuredAnalysis = preparationSnapshotCodec.readStructuredAnalysis(preparation);
        final StarterSelection originalStarterSelection = preparationSnapshotCodec.readStarterSelection(preparation);
        final ResolvedHeavyModel resolvedHeavyModel = resolveBoundHeavyModel(
                preparation,
                userId,
                heavyModelKeyOverride,
                heavyModelAuthSourceOverride);
        final DeterministicPlanningProfile planningProfile = deterministicPlanningPolicyService.deriveProfile(structuredAnalysis);
        final PlanningModelPolicy planningModelPolicy = planningModelPolicyService.resolvePolicy(
                planningProfile.effectiveComplexity(),
                resolvedHeavyModel,
                originalStarterSelection.docsOnlyFallback());
        final StartAuthorization startAuthorization = lifecycleAuthority.authorizeStart(
                preparation,
                request,
                structuredAnalysis,
                planningProfile,
                originalStarterSelection,
                planningModelPolicy);

        final ProjectPlanningPreviewEntity acceptedPreview = startAuthorization.acceptedPreview();
        final StarterSelection selectedStarterSelection = startAuthorization.selectedStarterSelection();
        final CanonicalBlueprint acceptedBlueprintPlan = startAuthorization.previewBlueprint();
        final PlanningGovernanceSnapshot governanceSnapshot = acceptedPreview == null
                ? planningGovernanceService.createSnapshot(new PlanningGovernanceContext(
                        preparation,
                        structuredAnalysis,
                        planningProfile,
                        lifecycleAuthority.selectedStarterKeyFor(selectedStarterSelection),
                        preparation.getIdeTarget(),
                        planningModelPolicy,
                        PlanningPreview.CURRENT_CONTRACT_VERSION))
                : planningGovernanceSnapshotCodec.readSnapshot(acceptedPreview.getGovernanceSnapshotJson());
        planningTelemetryService.recordStartReuse(governanceSnapshot, acceptedPreview != null);
        final IdeTarget ideTarget = parseIdeTarget(preparation.getIdeTarget());
        final String creditReservationReference = UUID.randomUUID().toString();
        final ProjectRequest projectRequest = new ProjectRequest(
                preparation.getProjectName(),
                preparation.getIdeTarget(),
                preparation.getPromptSummary(),
                null);

        creditManagementService.reserveCredit(userId);
        creditTransactionService.recordProjectGenerationReservation(
                userId,
                preparation.getProjectName(),
                creditReservationReference);

        final var project = projectStateService.createProjectRecord(
                userId,
                projectRequest,
                ideTarget,
                resolvedHeavyModel,
                creditReservationReference);
        preparation.markAsStarted(project.getId());

        final PreparedGenerationLaunch launch = new PreparedGenerationLaunch(
                preparation.getId(),
                acceptedPreview == null ? null : acceptedPreview.getId(),
                preparation.getChatId(),
                preparation.getChatMessageCount(),
                preparation.getProjectName(),
                preparation.getIdeTarget(),
                preparation.getPromptSummary(),
                structuredAnalysis,
                selectedStarterSelection,
                acceptedBlueprintPlan,
                acceptedPreview == null
                        ? null
                        : PlanningDecisionPayloadMapper.fromLegacyQualitySummary(acceptedPreview.getQualitySummaryJson()),
                acceptedPreview == null
                        ? null
                        : PlanningDecisionPayloadMapper.fromLegacyRepairStatus(acceptedPreview.getRepairStatusJson()));
        final RunCreatedPayload runCreatedPayload = new RunCreatedPayload(
                project.getId(),
                preparation.getId(),
                preparation.getProjectName(),
                "PENDING",
                ideTarget.name().toLowerCase(Locale.ROOT));

        return new PreparedGenerationStartContext(
                project.getId(),
                lifecycleAuthority.selectedStarterKeyFor(selectedStarterSelection),
                ideTarget,
                creditReservationReference,
                resolvedHeavyModel,
                launch,
                runCreatedPayload);
    }

    private ResolvedHeavyModel resolveBoundHeavyModel(
            final ProjectGenerationPreparationEntity preparation,
            final UUID userId,
            final String heavyModelKeyOverride,
            final CredentialSource heavyModelAuthSourceOverride) {
        final boolean hasExplicitOverride = (heavyModelKeyOverride != null && !heavyModelKeyOverride.isBlank())
                || heavyModelAuthSourceOverride != null;
        final ResolvedHeavyModel snapshotBoundHeavyModel = hasExplicitOverride
                ? null
                : preparationSnapshotCodec.readResolvedHeavyModel(preparation);
        final ResolvedHeavyModel resolvedHeavyModel = hasExplicitOverride
                ? heavyModelResolutionService.resolve(userId, heavyModelKeyOverride, heavyModelAuthSourceOverride)
                : snapshotBoundHeavyModel != null
                        ? snapshotBoundHeavyModel
                        : heavyModelResolutionService.resolve(userId, null, null);
        resolvedHeavyModelRuntimeBindingManager.bind(resolvedHeavyModel);
        return resolvedHeavyModel;
    }

    private IdeTarget parseIdeTarget(final String ideTarget) {
        try {
            return IdeTarget.valueOf(ideTarget.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return IdeTarget.GENERIC;
        }
    }
}
