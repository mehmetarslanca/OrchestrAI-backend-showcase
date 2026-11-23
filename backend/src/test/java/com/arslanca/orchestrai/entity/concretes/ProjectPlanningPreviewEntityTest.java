package com.arslanca.orchestrai.entity.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;

class ProjectPlanningPreviewEntityTest {

    @Test
    void shouldOnlyAllowAcceptanceFromReadyForReviewState() {
        final ProjectPlanningPreviewEntity preview = ProjectPlanningPreviewEntity.createDraft(
                UUID.randomUUID(),
                "starter",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                PlanningPreview.CURRENT_CONTRACT_VERSION);

        assertThatThrownBy(preview::markAccepted)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("review-ready");

        preview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        preview.markAccepted();
        assertThat(preview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.ACCEPTED);

        assertThatThrownBy(preview::markAccepted)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("review-ready");
    }

    @Test
    void shouldOnlyAllowMarkingFailedFromDraftState() {
        final ProjectPlanningPreviewEntity preview = ProjectPlanningPreviewEntity.createDraft(
                UUID.randomUUID(),
                "starter",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                PlanningPreview.CURRENT_CONTRACT_VERSION);

        preview.markFailed("Something went wrong");
        assertThat(preview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.FAILED);
        assertThat(preview.getRepairStatusJson()).containsEntry("status", "failed");
        assertThat(preview.getRepairStatusJson()).containsEntry("reason", "Something went wrong");

        assertThatThrownBy(() -> preview.markFailed("Again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft previews");
    }

    @Test
    void shouldAllowMarkingRejected() {
        final ProjectPlanningPreviewEntity preview = ProjectPlanningPreviewEntity.createDraft(
                UUID.randomUUID(),
                "starter",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                PlanningPreview.CURRENT_CONTRACT_VERSION);
                
        preview.markRejected(Map.of("score", 45), Map.of("attempts", 2), Map.of("partial", true));
        
        assertThat(preview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.REJECTED);
        assertThat(preview.getQualitySummaryJson()).containsEntry("score", 45);
        assertThat(preview.getRepairStatusJson()).containsEntry("attempts", 2);
    }
    
    @Test
    void shouldAllowMarkingInvalidated() {
        final ProjectPlanningPreviewEntity preview = ProjectPlanningPreviewEntity.createDraft(
                UUID.randomUUID(),
                "starter",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                PlanningPreview.CURRENT_CONTRACT_VERSION);
                
        preview.markInvalidated("Stale blueprint");
        
        assertThat(preview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        assertThat(preview.getRepairStatusJson()).containsEntry("status", "invalidated");
        assertThat(preview.getRepairStatusJson()).containsEntry("reason", "Stale blueprint");
    }
}
