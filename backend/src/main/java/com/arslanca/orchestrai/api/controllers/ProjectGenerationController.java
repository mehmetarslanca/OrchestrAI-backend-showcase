package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.api.adapters.SseEmitterAdapter;
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
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/project-generation")
public class ProjectGenerationController {

    private static final long SSE_TIMEOUT_MS = 0L;
    private static final Logger log = LoggerFactory.getLogger(ProjectGenerationController.class);

    private final ProjectGenerationPreparationService generationPreparationService;
    private final ProjectPlanningPreviewService planningPreviewService;
    private final PreparedProjectGenerationService preparedProjectGenerationService;

    public ProjectGenerationController(
            final ProjectGenerationPreparationService generationPreparationService,
            final ProjectPlanningPreviewService planningPreviewService,
            final PreparedProjectGenerationService preparedProjectGenerationService) {
        this.generationPreparationService = generationPreparationService;
        this.planningPreviewService = planningPreviewService;
        this.preparedProjectGenerationService = preparedProjectGenerationService;
    }

    @PostMapping("/prepare")
    public ResponseEntity<GenerationPreparationResponse> prepareGeneration(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final GenerationPreparationRequest request) {
        log.info("Preparing project generation review: userId={}, ideTarget={}", user.id(), request.ideTarget());
        final GenerationPreparationResponse response = generationPreparationService.prepareGeneration(user.id(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/preview")
    public ResponseEntity<PlanningPreview> generatePreview(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final GenerationPreviewRequest request) {
        log.info("Generating planning preview: userId={}, preparationId={}", user.id(), request.preparationId());
        return ResponseEntity.ok(planningPreviewService.generatePreview(user.id(), request));
    }

    @GetMapping("/previews/{previewId}")
    public ResponseEntity<PlanningPreview> getPreview(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final UUID previewId) {
        log.info("Loading planning preview: userId={}, previewId={}", user.id(), previewId);
        return ResponseEntity.ok(planningPreviewService.getPreview(user.id(), previewId));
    }

    @PostMapping("/previews/{previewId}/accept")
    public ResponseEntity<PlanningPreview> acceptPreview(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final UUID previewId) {
        log.info("Accepting planning preview: userId={}, previewId={}", user.id(), previewId);
        return ResponseEntity.ok(planningPreviewService.acceptPreview(user.id(), previewId));
    }

    @PostMapping(value = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startGeneration(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final GenerationStartRequest request) {
        log.info("Starting reviewed project generation: userId={}, preparationId={}", user.id(), request.preparationId());

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        final SseEventPublisher publisher = new SseEmitterAdapter(emitter);
        preparedProjectGenerationService.startGeneration(user.id(), request, publisher);
        return emitter;
    }

    @PostMapping("/{projectId}/cancel")
    public ResponseEntity<Void> cancelGeneration(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final UUID projectId) {
        log.info("Cancelling project generation for user: {}, projectId: {}", user.id(), projectId);
        preparedProjectGenerationService.cancelGeneration(user.id(), projectId);
        return ResponseEntity.accepted().build();
    }
}
