package com.arslanca.orchestrai.business.planning.preview;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.abstracts.StarterSelectionResolver;
import com.arslanca.orchestrai.business.concretes.ResolvedHeavyModelRuntimeBindingManager;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.planning.preparation.PreparationSnapshotCodec;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;

@Service
public class PreviewGenerationContextResolver {

    private final ProjectGenerationPreparationRepository preparationRepository;
    private final StarterSelectionResolver starterSelectionResolver;
    private final HeavyModelResolutionService heavyModelResolutionService;
    private final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;
    private final PlanningModelPolicyService planningModelPolicyService;
    private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;
    private final PlanningLifecycleAuthority planningLifecycleAuthority;
    private final PreparationSnapshotCodec preparationSnapshotCodec;

    public PreviewGenerationContextResolver(
            final ProjectGenerationPreparationRepository preparationRepository,
            final StarterSelectionResolver starterSelectionResolver,
            final HeavyModelResolutionService heavyModelResolutionService,
            final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager,
            final PlanningModelPolicyService planningModelPolicyService,
            final DeterministicPlanningPolicyService deterministicPlanningPolicyService,
            final PlanningLifecycleAuthority planningLifecycleAuthority,
            final PreparationSnapshotCodec preparationSnapshotCodec) {
        this.preparationRepository = preparationRepository;
        this.starterSelectionResolver = starterSelectionResolver;
        this.heavyModelResolutionService = heavyModelResolutionService;
        this.resolvedHeavyModelRuntimeBindingManager = resolvedHeavyModelRuntimeBindingManager;
        this.planningModelPolicyService = planningModelPolicyService;
        this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
        this.planningLifecycleAuthority = planningLifecycleAuthority;
        this.preparationSnapshotCodec = preparationSnapshotCodec;
    }

    public PreviewGenerationContext resolve(final UUID userId, final GenerationPreviewRequest request) {
        final ProjectGenerationPreparationEntity preparation = preparationRepository.findByIdAndUserId(request.preparationId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation preparation not found or access denied"));
        planningLifecycleAuthority.ensurePreviewGenerationAllowed(preparation);
        final StructuredAnalysis structuredAnalysis = preparationSnapshotCodec.readStructuredAnalysis(preparation);
        final StarterSelection originalStarterSelection = preparationSnapshotCodec.readStarterSelection(preparation);
        final StarterSelection selectedStarterSelection = starterSelectionResolver.resolveSelectedStarter(
                originalStarterSelection,
                request.starterKey());
        final ResolvedHeavyModel resolvedHeavyModel = resolveBoundHeavyModel(preparation, userId);
        final DeterministicPlanningProfile planningProfile = deterministicPlanningPolicyService.deriveProfile(structuredAnalysis);
        final PlanningModelPolicy planningModelPolicy = planningModelPolicyService.resolvePolicy(
                planningProfile.effectiveComplexity(),
                resolvedHeavyModel,
                selectedStarterSelection.docsOnlyFallback());

        return new PreviewGenerationContext(
                preparation,
                structuredAnalysis,
                planningProfile,
                selectedStarterSelection,
                resolvedHeavyModel,
                planningModelPolicy);
    }

    private ResolvedHeavyModel resolveBoundHeavyModel(
            final ProjectGenerationPreparationEntity preparation,
            final UUID userId) {
        final ResolvedHeavyModel snapshotBoundHeavyModel = preparationSnapshotCodec.readResolvedHeavyModel(preparation);
        final ResolvedHeavyModel resolvedHeavyModel = snapshotBoundHeavyModel != null
                ? snapshotBoundHeavyModel
                : heavyModelResolutionService.resolve(userId, null, null);
        resolvedHeavyModelRuntimeBindingManager.bind(resolvedHeavyModel);
        return resolvedHeavyModel;
    }
}
