package com.arslanca.orchestrai.business.concretes;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arslanca.orchestrai.business.abstracts.BlueprintPlanningService;
import com.arslanca.orchestrai.business.abstracts.ProjectPlanningPreviewService;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.planning.lifecycle.PreviewClaim;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlanningResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

import com.arslanca.orchestrai.business.planning.preview.PlanningPreviewFinalizer;
import com.arslanca.orchestrai.business.planning.preview.PlanningPreviewProjection;
import com.arslanca.orchestrai.business.planning.preview.PreviewGenerationContext;
import com.arslanca.orchestrai.business.planning.preview.PreviewGenerationContextResolver;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshotCodec;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;

@Service
public class ProjectPlanningPreviewManager implements ProjectPlanningPreviewService {

    private static final Logger log = LoggerFactory.getLogger(ProjectPlanningPreviewManager.class);

    private final ProjectGenerationPreparationRepository preparationRepository;
    private final ProjectPlanningPreviewRepository planningPreviewRepository;
    private final PreviewGenerationContextResolver previewGenerationContextResolver;
    private final BlueprintPlanningService blueprintPlanningService;
    private final PlanningLifecycleAuthority lifecycleAuthority;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final PlanningPreviewFinalizer planningPreviewFinalizer;
    private final PlanningPreviewProjection planningPreviewProjection;
    private final PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec;
    private final PlanningTelemetryService planningTelemetryService;

    public ProjectPlanningPreviewManager(
            final ProjectGenerationPreparationRepository preparationRepository,
            final ProjectPlanningPreviewRepository planningPreviewRepository,
            final PreviewGenerationContextResolver previewGenerationContextResolver,
            final BlueprintPlanningService blueprintPlanningService,
            final PlanningLifecycleAuthority lifecycleAuthority,
            final org.springframework.transaction.support.TransactionTemplate transactionTemplate,
            final PlanningPreviewFinalizer planningPreviewFinalizer,
            final PlanningPreviewProjection planningPreviewProjection,
            final PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec,
            final PlanningTelemetryService planningTelemetryService) {
        this.preparationRepository = preparationRepository;
        this.planningPreviewRepository = planningPreviewRepository;
        this.previewGenerationContextResolver = previewGenerationContextResolver;
        this.blueprintPlanningService = blueprintPlanningService;
        this.lifecycleAuthority = lifecycleAuthority;
        this.transactionTemplate = transactionTemplate;
        this.planningPreviewFinalizer = planningPreviewFinalizer;
        this.planningPreviewProjection = planningPreviewProjection;
        this.planningGovernanceSnapshotCodec = planningGovernanceSnapshotCodec;
        this.planningTelemetryService = planningTelemetryService;
    }

    @Override
    public PlanningPreview generatePreview(final UUID userId, final GenerationPreviewRequest request) {
        final PreviewGenerationContext previewContext = previewGenerationContextResolver.resolve(userId, request);

        final PreviewClaim previewClaim = transactionTemplate.execute(status -> {
            final ProjectGenerationPreparationEntity lockedPreparation = preparationRepository
                    .findByIdAndUserIdForUpdate(request.preparationId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Generation preparation not found or access denied"));
            lifecycleAuthority.ensurePreviewGenerationAllowed(lockedPreparation);
            return lifecycleAuthority.claimPreview(
                    lockedPreparation,
                    previewContext.structuredAnalysis(),
                    previewContext.planningProfile(),
                    previewContext.selectedStarterSelection(),
                    previewContext.planningModelPolicy());
        });

        if (!previewClaim.requiresPlanning()) {
            final ProjectPlanningPreviewEntity reusablePreview = previewClaim.preview();
            log.info("Reusing cached planning preview: preparationId={}, previewId={}, status={}",
                    previewContext.preparation().getId(),
                    reusablePreview.getId(),
                    reusablePreview.getPreviewStatus());
            return planningPreviewProjection.toPlanningPreview(reusablePreview);
        }

        final ProjectPlanningPreviewEntity initializedPreview = previewClaim.preview();

        final BlueprintPlanningResult planningResult;
        try {
            planningResult = blueprintPlanningService.planBlueprint(
                    previewContext.structuredAnalysis(),
                    previewContext.preparation().getIdeTarget(),
                    previewContext.resolvedHeavyModel(),
                    previewContext.selectedStarterSelection(),
                    previewContext.planningModelPolicy());
        } catch (final RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> planningPreviewFinalizer.markFailed(initializedPreview.getId(), exception.getMessage()));
            throw exception;
        }

        return transactionTemplate.execute(status -> planningPreviewProjection.toPlanningPreview(
                planningPreviewFinalizer.finalizePlannedPreview(initializedPreview.getId(), planningResult)));
    }

    @Override
    @Transactional(readOnly = true)
    public PlanningPreview getPreview(final UUID userId, final UUID previewId) {
        return planningPreviewProjection.toPlanningPreview(lifecycleAuthority.loadOwnedPreview(userId, previewId));
    }

    @Override
    @Transactional(noRollbackFor = com.arslanca.orchestrai.core.exception.type.PreparationStateException.class)
    public PlanningPreview acceptPreview(final UUID userId, final UUID previewId) {
        final ProjectPlanningPreviewEntity previewEntity = lifecycleAuthority.loadOwnedPreview(userId, previewId);
        final ProjectGenerationPreparationEntity preparation = preparationRepository
                .findByIdAndUserIdForUpdate(previewEntity.getPreparationId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation preparation not found or access denied"));

        lifecycleAuthority.ensurePreviewAcceptanceAllowed(preparation, previewEntity);
        previewEntity.markAccepted();
        lifecycleAuthority.invalidateSupersededSiblingsOnAcceptance(
                previewEntity.getPreparationId(),
                previewEntity.getPreviewCacheKey(),
                previewEntity.getId());
        planningPreviewRepository.save(previewEntity);
        planningTelemetryService.recordPreviewAccepted(
                planningGovernanceSnapshotCodec.readSnapshot(previewEntity.getGovernanceSnapshotJson()));
        return planningPreviewProjection.toPlanningPreview(previewEntity);
    }
}
