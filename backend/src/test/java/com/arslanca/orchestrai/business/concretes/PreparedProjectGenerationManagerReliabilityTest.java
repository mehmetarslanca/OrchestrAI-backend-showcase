package com.arslanca.orchestrai.business.concretes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;

import com.arslanca.orchestrai.business.abstracts.ArtifactAssemblyService;
import com.arslanca.orchestrai.business.abstracts.DistributedSemaphoreService;
import com.arslanca.orchestrai.business.abstracts.GenerationFailureCompensationService;
import com.arslanca.orchestrai.business.abstracts.OrchestrationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationCancellationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationFinalizationService;
import com.arslanca.orchestrai.business.abstracts.ScaffoldMaterializationService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.generation.runtime.PreparedGenerationPipelineRunner;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.concretes.cancellation.GenerationCancellationToken;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PreparedGenerationLaunch;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.arslanca.orchestrai.core.exception.type.ProjectGenerationCancelledException;
import com.arslanca.orchestrai.entity.enums.IdeTarget;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
public class PreparedProjectGenerationManagerReliabilityTest {

  @Mock
  private DistributedSemaphoreService distributedSemaphoreService;
  @Mock
  private GenerationFailureCompensationService compensationService;
  @Mock
  private ProjectStateService projectStateService;
  @Mock
  private ProjectGenerationCancellationService cancellationService;
  @Mock
  private OrchestrationService orchestrationService;
  @Mock
  private SseEventPublisher publisher;
  @Mock
  private TaskScheduler taskScheduler;
  @Mock
  private PlanningLifecycleAuthority lifecycleAuthority;
  @Mock
  private ProjectGenerationFinalizationService projectGenerationFinalizationService;
  @Mock
  private ScaffoldMaterializationService scaffoldMaterializationService;
  @Mock
  private ArtifactAssemblyService artifactAssemblyService;

  private PreparedGenerationPipelineRunner runner;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient().when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class))).thenReturn(null);
    runner = new PreparedGenerationPipelineRunner(
        orchestrationService,
        projectStateService,
        new SyncTaskExecutor(),
        taskScheduler,
        distributedSemaphoreService,
        compensationService,
        cancellationService,
        projectGenerationFinalizationService,
        lifecycleAuthority,
        scaffoldMaterializationService,
        artifactAssemblyService,
        new SimpleMeterRegistry(),
        new BlueprintPlanCompatibilityAdapter());
  }

  @Test
  void executePipelineAsync_ShouldCompensateAndRefund_WhenSemaphoreUnavailable() {
    UUID projectId = UUID.randomUUID();
    GenerationCancellationToken token = mock(GenerationCancellationToken.class);

    when(distributedSemaphoreService.tryAcquire(anyString(), eq(projectId.toString())))
        .thenThrow(new InfrastructureUnavailableException("Redis is down"));

    runner.run(publisher, projectId, UUID.randomUUID(), "ref1",
        mock(PreparedGenerationLaunch.class), IdeTarget.CURSOR, mock(ResolvedHeavyModel.class), token);

    verify(compensationService).compensateFailure(
        eq(projectId),
        any(InfrastructureUnavailableException.class),
        eq(true),
        eq(GenerationFailureReason.SEMAPHORE_UNAVAILABLE));
    verify(publisher).completeWithError(any(InfrastructureUnavailableException.class));
  }

  @Test
  void executePipelineAsync_ShouldCompensateAndRefund_OnRuntimeError() {
    UUID projectId = UUID.randomUUID();
    GenerationCancellationToken token = mock(GenerationCancellationToken.class);
    PreparedGenerationLaunch launch = mock(PreparedGenerationLaunch.class);

    when(distributedSemaphoreService.tryAcquire(anyString(), eq(projectId.toString()))).thenReturn(true);
    doThrow(new RuntimeException("Unexpected LLM error"))
        .when(orchestrationService).orchestratePrepared(any(), any(), any(), any(), any(), any(), any(GenerationUsageContext.class));

    runner.run(publisher, projectId, UUID.randomUUID(), "ref1",
        launch, IdeTarget.CURSOR, mock(ResolvedHeavyModel.class), token);

    verify(compensationService).compensateFailure(
        eq(projectId),
        any(RuntimeException.class),
        eq(true), // Audit Fix requires refund = true
        eq(GenerationFailureReason.PIPELINE_FAILURE));
  }

  @Test
  void executePipelineAsync_ShouldNotRefund_WhenCancelledAfterProcessingStarts() {
    UUID projectId = UUID.randomUUID();
    GenerationCancellationToken token = mock(GenerationCancellationToken.class);
    PreparedGenerationLaunch launch = mock(PreparedGenerationLaunch.class);

    when(distributedSemaphoreService.tryAcquire(anyString(), eq(projectId.toString()))).thenReturn(true);
    doThrow(new ProjectGenerationCancelledException(projectId, GenerationFailureReason.USER_CANCELLED))
        .when(orchestrationService).orchestratePrepared(any(), any(), any(), any(), any(), any(), any(GenerationUsageContext.class));

    runner.run(publisher, projectId, UUID.randomUUID(), "ref1",
        launch, IdeTarget.CURSOR, mock(ResolvedHeavyModel.class), token);

    verify(projectStateService).markProjectAsProcessing(projectId);

    verify(compensationService).compensateFailure(
        eq(projectId),
        any(ProjectGenerationCancelledException.class),
        eq(false), // Because refundEligible = false after markProjectAsProcessing
        eq(GenerationFailureReason.USER_CANCELLED));
  }
}
