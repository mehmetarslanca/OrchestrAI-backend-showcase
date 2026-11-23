package com.arslanca.orchestrai.business.planning.preview;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
import com.arslanca.orchestrai.business.planning.blueprint.PreviewPayloadCodec;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

@Service
public class PlanningPreviewProjection {

    private final PreviewPayloadCodec previewPayloadCodec;
    private final BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter;

    public PlanningPreviewProjection(
            final PreviewPayloadCodec previewPayloadCodec,
            final BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter) {
        this.previewPayloadCodec = previewPayloadCodec;
        this.blueprintPlanCompatibilityAdapter = blueprintPlanCompatibilityAdapter;
    }

    public PlanningPreview toPlanningPreview(final ProjectPlanningPreviewEntity entity) {
        final BlueprintPlan blueprintPayload = entity.getBlueprintPayloadJson().isEmpty()
                ? null
                : blueprintPlanCompatibilityAdapter.toBlueprintPlan(
                        previewPayloadCodec.decodeCanonical(entity.getBlueprintPayloadJson()));
        return new PlanningPreview(
                entity.getId(),
                entity.getPreparationId(),
                entity.getSelectedStarterKey(),
                entity.getPlanningMode(),
                entity.getPreviewStatus(),
                entity.getQualitySummaryJson(),
                entity.getRepairStatusJson(),
                entity.getPreviewContractVersion(),
                blueprintPayload);
    }
}
