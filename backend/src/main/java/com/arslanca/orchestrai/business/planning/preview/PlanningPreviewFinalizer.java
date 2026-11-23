package com.arslanca.orchestrai.business.planning.preview;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlanningResult;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshotCodec;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.arslanca.orchestrai.business.planning.blueprint.PreviewPayloadCodec;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;

@Service
public class PlanningPreviewFinalizer {

    private final ProjectPlanningPreviewRepository planningPreviewRepository;
    private final PreviewPayloadCodec previewPayloadCodec;
    private final PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec;
    private final PlanningTelemetryService planningTelemetryService;

    public PlanningPreviewFinalizer(
            final ProjectPlanningPreviewRepository planningPreviewRepository,
            final PreviewPayloadCodec previewPayloadCodec,
            final PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec,
            final PlanningTelemetryService planningTelemetryService) {
        this.planningPreviewRepository = planningPreviewRepository;
        this.previewPayloadCodec = previewPayloadCodec;
        this.planningGovernanceSnapshotCodec = planningGovernanceSnapshotCodec;
        this.planningTelemetryService = planningTelemetryService;
    }

    public void markFailed(final java.util.UUID previewId, final String failureMessage) {
        final ProjectPlanningPreviewEntity persistedPreview = loadPreview(previewId);
        if (persistedPreview.getPreviewStatus() != PlanningPreviewStatus.DRAFT) {
            return;
        }
        persistedPreview.markFailed(failureMessage);
        final ProjectPlanningPreviewEntity savedPreview = planningPreviewRepository.save(persistedPreview);
        planningTelemetryService.recordPreviewOutcome(
                planningGovernanceSnapshotCodec.readSnapshot(savedPreview.getGovernanceSnapshotJson()),
                PlanningPreviewStatus.FAILED);
    }

    public ProjectPlanningPreviewEntity finalizePlannedPreview(
            final java.util.UUID previewId,
            final BlueprintPlanningResult planningResult) {
        final ProjectPlanningPreviewEntity persistedPreview = loadPreview(previewId);
        if (persistedPreview.getPreviewStatus() != PlanningPreviewStatus.DRAFT) {
            return persistedPreview;
        }

        final PlanningPreviewStatus previewStatus;
        if (planningResult.accepted()) {
            persistedPreview.markReadyForReview(
                    planningResult.qualitySummaryPayload(),
                    planningResult.repairStatusPayload(),
                    previewPayloadCodec.encode(planningResult.canonicalBlueprint()));
            previewStatus = PlanningPreviewStatus.READY_FOR_REVIEW;
        } else {
            persistedPreview.markRejected(
                    planningResult.qualitySummaryPayload(),
                    planningResult.repairStatusPayload(),
                    previewPayloadCodec.encode(planningResult.canonicalBlueprint()));
            previewStatus = PlanningPreviewStatus.REJECTED;
        }
        final ProjectPlanningPreviewEntity savedPreview = planningPreviewRepository.save(persistedPreview);
        final var governanceSnapshot = planningGovernanceSnapshotCodec.readSnapshot(savedPreview.getGovernanceSnapshotJson());
        planningTelemetryService.recordPreviewOutcome(
                governanceSnapshot,
                previewStatus);
        planningTelemetryService.recordPreviewFinalized(
                governanceSnapshot,
                previewStatus,
                planningResult.qualitySummary(),
                planningResult.repairStatus());
        return savedPreview;
    }

    private ProjectPlanningPreviewEntity loadPreview(final java.util.UUID previewId) {
        return planningPreviewRepository.findById(previewId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning preview not found"));
    }
}
