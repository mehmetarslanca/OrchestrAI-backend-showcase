package com.arslanca.orchestrai.business.planning.lifecycle;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;

@Service
public class PreparationStateGuard {

    public void ensurePreviewGenerationAllowed(final ProjectGenerationPreparationEntity preparation) {
        if (preparation.getStatus() == ProjectGenerationPreparationStatus.STARTED) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREPARATION_ALREADY_STARTED,
                    "Generation already started for this preparation.");
        }
        if (preparation.getStatus() == ProjectGenerationPreparationStatus.INVALIDATED) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREPARATION_INVALIDATED,
                    "This preparation is no longer valid. Please prepare generation again.");
        }
    }

    public void ensurePreviewAcceptanceAllowed(
            final ProjectGenerationPreparationEntity preparation,
            final ProjectPlanningPreviewEntity preview) {
        ensurePreviewGenerationAllowed(preparation);
        if (preview.getPreviewStatus() != PlanningPreviewStatus.READY_FOR_REVIEW) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREVIEW_NOT_READY_FOR_ACCEPTANCE,
                    "Planning preview must be ready for review before it can be accepted.");
        }
    }

    public void ensureStartAllowed(final ProjectGenerationPreparationEntity preparation) {
        if (preparation.getStatus() == ProjectGenerationPreparationStatus.STARTED) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREPARATION_ALREADY_STARTED,
                    "This generation review has already been used to start a project.");
        }

        if (preparation.getStatus() == ProjectGenerationPreparationStatus.INVALIDATED) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREPARATION_INVALIDATED,
                    "This generation review is no longer valid. Please prepare generation again.");
        }
    }
}
