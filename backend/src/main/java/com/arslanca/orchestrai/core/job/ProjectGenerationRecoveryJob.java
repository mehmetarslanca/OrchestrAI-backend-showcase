package com.arslanca.orchestrai.core.job;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.abstracts.GenerationFailureCompensationService;
import com.arslanca.orchestrai.business.concretes.ProjectStateService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.core.config.PipelineRecoveryProperties;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;

@Component
public class ProjectGenerationRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(ProjectGenerationRecoveryJob.class);

    private final ProjectRepository projectRepository;
    private final GenerationFailureCompensationService generationFailureCompensationService;
    private final ProjectStateService projectStateService;
    private final PipelineRecoveryProperties properties;

    public ProjectGenerationRecoveryJob(
            final ProjectRepository projectRepository,
            final GenerationFailureCompensationService generationFailureCompensationService,
            final ProjectStateService projectStateService,
            final PipelineRecoveryProperties properties) {
        this.projectRepository = projectRepository;
        this.generationFailureCompensationService = generationFailureCompensationService;
        this.projectStateService = projectStateService;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${orchestrai.pipeline.recovery.check-interval:60000}")
    public void recoverStaleProjects() {
        recoverStalePending();
        recoverStaleProcessing();
    }

    private void recoverStalePending() {
        final OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(properties.getStalePendingMinutes());
        final List<ProjectEntity> stalePending = projectRepository.findByStatusAndUpdatedAtBefore(ProjectStatus.PENDING,
                threshold);

        if (!stalePending.isEmpty()) {
            log.warn("Recovery Job detected {} stale PENDING projects.", stalePending.size());
            for (ProjectEntity project : stalePending) {
                recover(project, ProjectStatus.PENDING,
                        "Project stuck in PENDING state. Dispatch failed or lost.");
            }
        }
    }

    private void recoverStaleProcessing() {
        final OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(properties.getStaleProcessingMinutes());
        final List<ProjectEntity> staleProcessing = projectRepository
                .findByStatusAndUpdatedAtBefore(ProjectStatus.PROCESSING, threshold);

        if (!staleProcessing.isEmpty()) {
            log.warn("Recovery Job detected {} stale PROCESSING projects.", staleProcessing.size());
            for (ProjectEntity project : staleProcessing) {
                recover(project, ProjectStatus.PROCESSING,
                        "System crashed during generation or pipeline timeout. Project aborted.");
            }
        }
    }

    private void recover(final ProjectEntity project, final ProjectStatus staleStatus, final String message) {
        try {
            projectStateService.appendProjectMetadata(project.getId(), Map.of(
                    ProjectStateService.TERMINAL_STATE_SOURCE_KEY, ProjectStateService.TERMINAL_STATE_SOURCE_RECOVERY_JOB,
                    ProjectStateService.STALE_PROJECT_STATUS_KEY, staleStatus.name()));
            generationFailureCompensationService.compensateFailure(
                    project.getId(),
                    new IllegalStateException(message),
                    true,
                    GenerationFailureReason.WORKER_FAILURE);
        } catch (final Exception exception) {
            log.error("Failed to recover stale {} project {}: {}", staleStatus, project.getId(),
                    exception.getMessage());
        }
    }
}
