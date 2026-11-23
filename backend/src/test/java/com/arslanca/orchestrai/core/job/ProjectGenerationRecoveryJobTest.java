package com.arslanca.orchestrai.core.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.abstracts.GenerationFailureCompensationService;
import com.arslanca.orchestrai.business.concretes.ProjectStateService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.core.config.PipelineRecoveryProperties;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationRecoveryJobTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GenerationFailureCompensationService generationFailureCompensationService;

    @Mock
    private ProjectStateService projectStateService;

    private PipelineRecoveryProperties properties;
    private ProjectGenerationRecoveryJob recoveryJob;

    @BeforeEach
    void setUp() {
        properties = new PipelineRecoveryProperties();
        properties.setStalePendingMinutes(2);
        properties.setStaleProcessingMinutes(30);
        recoveryJob = new ProjectGenerationRecoveryJob(
                projectRepository,
                generationFailureCompensationService,
                projectStateService,
                properties);
    }

    @Test
    void shouldRecoverStalePendingAndProcessingProjectsWithCanonicalReason() {
        final UUID pendingId = UUID.randomUUID();
        final UUID processingId = UUID.randomUUID();
        final ProjectEntity pendingProject = ProjectEntity.builder().id(pendingId).build();
        final ProjectEntity processingProject = ProjectEntity.builder()
                .id(processingId)
                .status(ProjectStatus.PROCESSING)
                .build();

        when(projectRepository.findByStatusAndUpdatedAtBefore(eq(ProjectStatus.PENDING), any(OffsetDateTime.class)))
                .thenReturn(List.of(pendingProject));
        when(projectRepository.findByStatusAndUpdatedAtBefore(eq(ProjectStatus.PROCESSING), any(OffsetDateTime.class)))
                .thenReturn(List.of(processingProject));

        recoveryJob.recoverStaleProjects();

        verify(projectStateService).appendProjectMetadata(eq(pendingId), argThat(metadata ->
                ProjectStateService.TERMINAL_STATE_SOURCE_RECOVERY_JOB.equals(
                        metadata.get(ProjectStateService.TERMINAL_STATE_SOURCE_KEY))
                        && ProjectStatus.PENDING.name().equals(metadata.get(ProjectStateService.STALE_PROJECT_STATUS_KEY))));
        verify(projectStateService).appendProjectMetadata(eq(processingId), argThat(metadata ->
                ProjectStateService.TERMINAL_STATE_SOURCE_RECOVERY_JOB.equals(
                        metadata.get(ProjectStateService.TERMINAL_STATE_SOURCE_KEY))
                        && ProjectStatus.PROCESSING.name().equals(metadata.get(ProjectStateService.STALE_PROJECT_STATUS_KEY))));

        verify(generationFailureCompensationService).compensateFailure(
                eq(pendingId), any(IllegalStateException.class), eq(true), eq(GenerationFailureReason.WORKER_FAILURE));
        verify(generationFailureCompensationService).compensateFailure(
                eq(processingId), any(IllegalStateException.class), eq(true), eq(GenerationFailureReason.WORKER_FAILURE));
    }
}
