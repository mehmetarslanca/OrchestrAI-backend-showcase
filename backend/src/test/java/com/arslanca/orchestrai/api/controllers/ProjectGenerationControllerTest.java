package com.arslanca.orchestrai.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.arslanca.orchestrai.business.abstracts.PreparedProjectGenerationService;
import com.arslanca.orchestrai.business.abstracts.ProjectPlanningPreviewService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import com.arslanca.orchestrai.entity.enums.UserRole;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationControllerTest {

    @Mock
    private ProjectGenerationPreparationService generationPreparationService;

    @Mock
    private ProjectPlanningPreviewService planningPreviewService;

    @Mock
    private PreparedProjectGenerationService preparedProjectGenerationService;

    private ProjectGenerationController controller;
    private AuthenticatedUser authenticatedUser;

    @BeforeEach
    void setUp() {
        controller = new ProjectGenerationController(generationPreparationService, planningPreviewService, preparedProjectGenerationService);
        authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), "memet", UserRole.USER, null);
    }

    @Test
    @DisplayName("prepareGeneration: gecerli istek ile review response doner")
    void shouldPrepareGeneration() {
        final GenerationPreparationRequest request = new GenerationPreparationRequest(
                "Acme Platform",
                "cursor",
                "Build an AI orchestration workspace",
                "WEB");
        final GenerationPreparationResponse response = new GenerationPreparationResponse(
                UUID.randomUUID(),
                "Acme Platform",
                "cursor",
                null,
                List.of(),
                false,
                null);

        when(generationPreparationService.prepareGeneration(authenticatedUser.id(), request)).thenReturn(response);

        final var entity = controller.prepareGeneration(authenticatedUser, request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isEqualTo(response);
    }

    @Test
    @DisplayName("startGeneration: SSE emitter doner ve canonical service'e delegate eder")
    void shouldStartGeneration() {
        final GenerationStartRequest request = new GenerationStartRequest(UUID.randomUUID(), null);

        final SseEmitter emitter = controller.startGeneration(authenticatedUser, request);

        assertThat(emitter).isNotNull();
        verify(preparedProjectGenerationService).startGeneration(eq(authenticatedUser.id()), eq(request), any(SseEventPublisher.class));
    }

    @Test
    @DisplayName("generatePreview: preview response doner")
    void shouldGeneratePreview() {
        final GenerationPreviewRequest request = new GenerationPreviewRequest(UUID.randomUUID(), null);
        final PlanningPreview preview = new PlanningPreview(
                UUID.randomUUID(),
                request.preparationId(),
                "nextjs-saas-shell",
                com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus.READY_FOR_REVIEW,
                java.util.Map.of(),
                java.util.Map.of(),
                PlanningPreview.CURRENT_CONTRACT_VERSION,
                null);

        when(planningPreviewService.generatePreview(authenticatedUser.id(), request)).thenReturn(preview);

        final var entity = controller.generatePreview(authenticatedUser, request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isEqualTo(preview);
    }

    @Test
    @DisplayName("cancelGeneration: 202 Accepted doner")
    void shouldCancelGeneration() {
        final UUID projectId = UUID.randomUUID();

        final var entity = controller.cancelGeneration(authenticatedUser, projectId);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(preparedProjectGenerationService).cancelGeneration(authenticatedUser.id(), projectId);
    }
}
