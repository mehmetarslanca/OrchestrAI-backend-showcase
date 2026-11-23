package com.arslanca.orchestrai.business.planning.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationSourceChannel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

class PreparationStateGuardTest {

    private PreparationStateGuard guard;
    private ProjectGenerationPreparationEntity preparation;
    private ProjectPlanningPreviewEntity preview;

    @BeforeEach
    void setUp() {
        guard = new PreparationStateGuard();
        preparation = ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                GenerationSourceChannel.WEB.name(),
                "Acme Platform",
                "cursor",
                "Build an orchestration workspace",
                Map.of("projectName", "Acme Platform"),
                Map.of("recommendedStarter", Map.of("key", "nextjs-saas-shell")),
                "1.0",
                Map.of()
        );

        preview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                "some-cache-key",
                "1.0"
        );
    }

    @Nested
    class EnsurePreviewGenerationAllowed {
        @Test
        void shouldAllowWhenPreparationIsPending() {
            guard.ensurePreviewGenerationAllowed(preparation);
            // No exception thrown
        }

        @Test
        void shouldThrowWhenPreparationIsStarted() {
            preparation.markAsStarted(UUID.randomUUID());

            assertThatThrownBy(() -> guard.ensurePreviewGenerationAllowed(preparation))
                    .isInstanceOf(LifecycleViolationException.class)
                    .hasMessageContaining("Generation already started")
                    .extracting("reason")
                    .isEqualTo(LifecycleViolationReason.PREPARATION_ALREADY_STARTED);
        }

        @Test
        void shouldThrowWhenPreparationIsInvalidated() {
            preparation.markAsInvalidated();

            assertThatThrownBy(() -> guard.ensurePreviewGenerationAllowed(preparation))
                    .isInstanceOf(LifecycleViolationException.class)
                    .hasMessageContaining("no longer valid")
                    .extracting("reason")
                    .isEqualTo(LifecycleViolationReason.PREPARATION_INVALIDATED);
        }
    }

    @Nested
    class EnsurePreviewAcceptanceAllowed {
        @Test
        void shouldAllowWhenPreviewIsReadyForReviewAndPreparationIsPending() {
            preview.markReadyForReview(Map.of(), Map.of(), Map.of());
            guard.ensurePreviewAcceptanceAllowed(preparation, preview);
            // No exception thrown
        }

        @Test
        void shouldThrowWhenPreviewIsNotReadyForReview() {
            assertThatThrownBy(() -> guard.ensurePreviewAcceptanceAllowed(preparation, preview))
                    .isInstanceOf(LifecycleViolationException.class)
                    .hasMessageContaining("ready for review")
                    .extracting("reason")
                    .isEqualTo(LifecycleViolationReason.PREVIEW_NOT_READY_FOR_ACCEPTANCE);
        }

        @Test
        void shouldThrowWhenPreparationIsStarted() {
            preview.markReadyForReview(Map.of(), Map.of(), Map.of());
            preparation.markAsStarted(UUID.randomUUID());

            assertThatThrownBy(() -> guard.ensurePreviewAcceptanceAllowed(preparation, preview))
                    .isInstanceOf(LifecycleViolationException.class)
                    .hasMessageContaining("Generation already started");
        }
    }

    @Nested
    class EnsureStartAllowed {
        @Test
        void shouldAllowWhenPreparationIsPending() {
            guard.ensureStartAllowed(preparation);
            // No exception thrown
        }

        @Test
        void shouldThrowWhenPreparationIsStarted() {
            preparation.markAsStarted(UUID.randomUUID());

            assertThatThrownBy(() -> guard.ensureStartAllowed(preparation))
                    .isInstanceOf(LifecycleViolationException.class)
                    .hasMessageContaining("already been used to start")
                    .extracting("reason")
                    .isEqualTo(LifecycleViolationReason.PREPARATION_ALREADY_STARTED);
        }

        @Test
        void shouldThrowWhenPreparationIsInvalidated() {
            preparation.markAsInvalidated();

            assertThatThrownBy(() -> guard.ensureStartAllowed(preparation))
                    .isInstanceOf(LifecycleViolationException.class)
                    .hasMessageContaining("no longer valid")
                    .extracting("reason")
                    .isEqualTo(LifecycleViolationReason.PREPARATION_INVALIDATED);
        }
    }
}
