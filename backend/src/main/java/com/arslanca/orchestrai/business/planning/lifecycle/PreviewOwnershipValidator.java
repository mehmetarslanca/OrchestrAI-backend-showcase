package com.arslanca.orchestrai.business.planning.lifecycle;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

@Service
public class PreviewOwnershipValidator {

    private final ProjectPlanningPreviewRepository planningPreviewRepository;
    private final ProjectGenerationPreparationRepository preparationRepository;

    public PreviewOwnershipValidator(
            final ProjectPlanningPreviewRepository planningPreviewRepository,
            final ProjectGenerationPreparationRepository preparationRepository) {
        this.planningPreviewRepository = planningPreviewRepository;
        this.preparationRepository = preparationRepository;
    }

    public ProjectPlanningPreviewEntity loadOwnedPreview(final UUID userId, final UUID previewId) {
        final ProjectPlanningPreviewEntity preview = planningPreviewRepository.findById(previewId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning preview not found"));
        preparationRepository.findByIdAndUserId(preview.getPreparationId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning preview not found or access denied"));
        return preview;
    }

    public ProjectPlanningPreviewEntity loadBoundPreview(
            final ProjectGenerationPreparationEntity preparation,
            final UUID previewId) {
        if (previewId == null) {
            return null;
        }

        final ProjectPlanningPreviewEntity preview = planningPreviewRepository.findById(previewId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning preview not found"));
        if (!preparation.getId().equals(preview.getPreparationId())) {
            throw new ResourceNotFoundException("Planning preview not found for this preparation");
        }
        return preview;
    }
}
