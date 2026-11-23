package com.arslanca.orchestrai.business.planning.lifecycle;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;

@Service
public class LegacyBridgePolicy {

    public boolean requiresAcceptedPreview(final GenerationPreparationResponse preparationResponse) {
        return preparationResponse.planningSummary() == null
                || preparationResponse.planningSummary().previewRequired()
                || preparationResponse.planningSummary().effectiveComplexity().requiresPreviewReview();
    }

    public boolean previewRequiresAcceptance(final PlanningPreview preview) {
        if (preview.previewStatus() == PlanningPreviewStatus.READY_FOR_REVIEW) {
            if (requiresExplicitReview(preview)) {
                throw new LifecycleViolationException(
                        LifecycleViolationReason.LEGACY_PREVIEW_REVIEW_REQUIRED,
                        "Planning preview requires explicit review and cannot be auto-accepted in the legacy bridge.");
            }
            return true;
        }
        if (preview.previewStatus() == PlanningPreviewStatus.ACCEPTED) {
            return false;
        }
        throw new LifecycleViolationException(
                LifecycleViolationReason.LEGACY_PREVIEW_NOT_READY,
                "Generated planning preview did not pass quality gates and cannot be auto-accepted.");
    }

    private boolean requiresExplicitReview(final PlanningPreview preview) {
        final PlanningQualitySummary qualitySummary = PlanningDecisionPayloadMapper
                .fromLegacyQualitySummary(preview.qualitySummary());
        return qualitySummary.previewRequired() || qualitySummary.effectiveComplexity().requiresPreviewReview();
    }
}
