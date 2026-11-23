package com.arslanca.orchestrai.business.planning.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;

class LegacyBridgePolicyTest {

    private final LegacyBridgePolicy policy = new LegacyBridgePolicy();

    @Test
    void shouldRequireAcceptedPreviewForEnterpriseScopeEvenWhenLegacyFlagIsFalse() {
        final GenerationPreparationResponse response = new GenerationPreparationResponse(
                UUID.randomUUID(),
                "Acme Platform",
                "cursor",
                null,
                List.of(),
                false,
                "",
                new PlanningSummary(
                        PlanningComplexity.MODERATE,
                        List.of(),
                        "",
                        PlanningMode.REDUCED_MULTI_PASS,
                        CapabilityRisk.MODERATE,
                        false,
                        false,
                        false,
                        "",
                        "model",
                        ModelCapability.HIGH,
                        false,
                        DegradeStrategy.NONE,
                        PlanningSummary.CURRENT_CONTRACT_VERSION,
                        PlanningComplexity.ENTERPRISE,
                        List.of(),
                        com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope.unknown()));

        assertThat(policy.requiresAcceptedPreview(response)).isTrue();
    }

    @Test
    void shouldAllowLegacyAutoAcceptForModerateSafePreview() {
        final PlanningPreview preview = new PlanningPreview(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "nextjs-saas-shell",
                PlanningMode.REDUCED_MULTI_PASS,
                PlanningPreviewStatus.READY_FOR_REVIEW,
                Map.of("previewRequired", false, "effectiveComplexity", "moderate"),
                Map.of(),
                PlanningPreview.CURRENT_CONTRACT_VERSION,
                null);

        assertThat(policy.previewRequiresAcceptance(preview)).isTrue();
    }

    @Test
    void shouldRejectLegacyAutoAcceptForComplexReviewRequiredPreview() {
        final PlanningPreview preview = new PlanningPreview(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                PlanningPreviewStatus.READY_FOR_REVIEW,
                Map.of("previewRequired", true, "effectiveComplexity", "complex"),
                Map.of(),
                PlanningPreview.CURRENT_CONTRACT_VERSION,
                null);

        assertThatThrownBy(() -> policy.previewRequiresAcceptance(preview))
                .isInstanceOfSatisfying(LifecycleViolationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(LifecycleViolationReason.LEGACY_PREVIEW_REVIEW_REQUIRED));
    }
}
