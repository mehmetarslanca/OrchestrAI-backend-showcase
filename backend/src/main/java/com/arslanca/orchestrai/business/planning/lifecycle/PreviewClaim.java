package com.arslanca.orchestrai.business.planning.lifecycle;

import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

public record PreviewClaim(
        ProjectPlanningPreviewEntity preview,
        boolean reusable) {

    public boolean requiresPlanning() {
        return !reusable;
    }
}
