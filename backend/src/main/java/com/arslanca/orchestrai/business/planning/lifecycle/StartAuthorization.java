package com.arslanca.orchestrai.business.planning.lifecycle;

import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

public record StartAuthorization(
        ProjectPlanningPreviewEntity acceptedPreview,
        StarterSelection selectedStarterSelection,
        CanonicalBlueprint previewBlueprint) {
}
