package com.arslanca.orchestrai.business.abstracts;

import java.util.UUID;

import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;

public interface ProjectPlanningPreviewService {

    PlanningPreview generatePreview(UUID userId, GenerationPreviewRequest request);

    PlanningPreview getPreview(UUID userId, UUID previewId);

    PlanningPreview acceptPreview(UUID userId, UUID previewId);
}
