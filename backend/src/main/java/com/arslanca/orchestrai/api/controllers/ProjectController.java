package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.ProjectGenerationService;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.project.ProjectResponse;
import com.arslanca.orchestrai.core.config.projectgeneration.ProjectGenerationFeatureProperties;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Legacy generate wrapper ve proje geçmişi endpoint'leri.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private static final long SSE_TIMEOUT_MS = 0L;
    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectGenerationService projectGenerationService;
    private final ProjectGenerationFeatureProperties featureProperties;
    private final MeterRegistry meterRegistry;

    public ProjectController(
            final ProjectGenerationService projectGenerationService,
            final ProjectGenerationFeatureProperties featureProperties,
            final MeterRegistry meterRegistry) {
        this.projectGenerationService = projectGenerationService;
        this.featureProperties = featureProperties;
        this.meterRegistry = meterRegistry;
    }


    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateBlueprint(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @Valid @RequestBody final ProjectRequest request) {
        recordLegacyMetric(featureProperties.isLegacyGenerateEnabled() ? "requested" : "disabled");
        if (!featureProperties.isLegacyGenerateEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Legacy project generation endpoint is disabled.");
        }

        log.info("Legacy generation wrapper initiated: userId={}, projectName={}, ideTarget={}",
                authenticatedUser.id(), request.projectName(), request.ideTarget());

        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        final com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher publisher = new com.arslanca.orchestrai.api.adapters.SseEmitterAdapter(
                emitter);

        projectGenerationService.generateBlueprint(authenticatedUser.id(), request, publisher);

        return emitter;
    }

    private void recordLegacyMetric(final String outcome) {
        meterRegistry.counter(
                "project_generation.legacy.endpoint",
                "endpoint", "projects_generate",
                "outcome", outcome)
                .increment();
    }

    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> getUserProjects(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @PageableDefault(size = 20) final Pageable pageable) {
        final Page<ProjectResponse> projects = projectGenerationService.getUserProjects(authenticatedUser.id(),
                pageable);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProjectById(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @PathVariable final UUID projectId) {
        final ProjectResponse project = projectGenerationService.getProjectById(authenticatedUser.id(), projectId);
        return ResponseEntity.ok(project);
    }

    @GetMapping("/{projectId}/artifact")
    public ResponseEntity<com.arslanca.orchestrai.business.dtos.project.ProjectArtifactResponse> getProjectArtifact(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @PathVariable final UUID projectId) {
        final com.arslanca.orchestrai.business.dtos.project.ProjectArtifactResponse artifact = projectGenerationService.getProjectArtifact(authenticatedUser.id(), projectId);
        return ResponseEntity.ok(artifact);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @PathVariable final UUID projectId) {
        log.info("Deleting project: userId={}, projectId={}", authenticatedUser.id(), projectId);
        projectGenerationService.deleteProject(authenticatedUser.id(), projectId);
        return ResponseEntity.noContent().build();
    }
}
