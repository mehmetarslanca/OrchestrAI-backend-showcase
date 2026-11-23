package com.arslanca.orchestrai.business.concretes;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arslanca.orchestrai.business.abstracts.ProjectGenerationService;
import com.arslanca.orchestrai.business.abstracts.PreparedProjectGenerationService;
import com.arslanca.orchestrai.business.abstracts.ProjectPlanningPreviewService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactResponse;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.project.ProjectResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.mapper.EntityMapper;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectArtifactRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectGenerationManager implements ProjectGenerationService {

    private final ProjectRepository projectRepository;
    private final ProjectArtifactRepository projectArtifactRepository;
    private final EntityMapper entityMapper;
    private final ProjectGenerationPreparationService preparationService;
    private final ProjectPlanningPreviewService planningPreviewService;
    private final PreparedProjectGenerationService preparedGenerationService;
    private final PlanningLifecycleAuthority lifecycleAuthority;

    @Override
    public UUID generateBlueprint(final UUID userId, final ProjectRequest request, final SseEventPublisher publisher) {
        final GenerationPreparationRequest prepRequest = new GenerationPreparationRequest(
                request.projectName(),
                request.ideTarget(),
                request.prompt(),
                null);
        final GenerationPreparationResponse prepResponse = preparationService.prepareGeneration(userId, prepRequest);
        UUID acceptedPreviewId = null;
        if (lifecycleAuthority.requiresAcceptedPreview(prepResponse)) {
            final var preview = planningPreviewService.generatePreview(
                    userId,
                    new GenerationPreviewRequest(prepResponse.preparationId(), null));
            if (lifecycleAuthority.previewRequiresAcceptance(preview)) {
                acceptedPreviewId = planningPreviewService.acceptPreview(userId, preview.previewId()).previewId();
            } else {
                acceptedPreviewId = preview.previewId();
            }
        }
        final GenerationStartRequest startRequest = new GenerationStartRequest(prepResponse.preparationId(), null, acceptedPreviewId);

        return preparedGenerationService.startGenerationWithOverride(
                userId,
                startRequest,
                request.heavyModelKey(),
                request.heavyModelAuthSource(),
                publisher);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectResponse> getUserProjects(final UUID userId, final Pageable pageable) {
        return projectRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(entityMapper::toProjectResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(final UUID userId, final UUID projectId) {
        return projectRepository.findById(projectId)
                .filter(project -> project.getUser().getId().equals(userId))
                .map(entityMapper::toProjectResponse)
                .orElseThrow(() -> ResourceNotFoundException.forProject(projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectArtifactResponse getProjectArtifact(final UUID userId, final UUID projectId) {

        return projectArtifactRepository.findByProjectIdAndProject_User_Id(projectId, userId)
                .map(entityMapper::toProjectArtifactResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Artifact not found for project"));
    }

    @Override
    @Transactional
    public void deleteProject(final UUID userId, final UUID projectId) {
        final var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project not found: " + projectId));

        if (!project.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("User does not own this project");
        }

        projectRepository.delete(project);
    }
}
