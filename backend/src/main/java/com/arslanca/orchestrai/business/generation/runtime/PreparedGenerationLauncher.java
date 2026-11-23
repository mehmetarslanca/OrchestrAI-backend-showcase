package com.arslanca.orchestrai.business.generation.runtime;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.GenerationFailureCompensationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationCancellationService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.concretes.cancellation.GenerationCancellationToken;
import com.arslanca.orchestrai.business.dtos.sse.SseProgressEvent;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class PreparedGenerationLauncher {

    private static final Logger log = LoggerFactory.getLogger(PreparedGenerationLauncher.class);

    private final TaskExecutor applicationTaskExecutor;
    private final ProjectGenerationCancellationService projectGenerationCancellationService;
    private final GenerationFailureCompensationService generationFailureCompensationService;
    private final PreparedGenerationPipelineRunner preparedGenerationPipelineRunner;
    private final MeterRegistry meterRegistry;

    public PreparedGenerationLauncher(
            final TaskExecutor applicationTaskExecutor,
            final ProjectGenerationCancellationService projectGenerationCancellationService,
            final GenerationFailureCompensationService generationFailureCompensationService,
            final PreparedGenerationPipelineRunner preparedGenerationPipelineRunner,
            final MeterRegistry meterRegistry) {
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.projectGenerationCancellationService = projectGenerationCancellationService;
        this.generationFailureCompensationService = generationFailureCompensationService;
        this.preparedGenerationPipelineRunner = preparedGenerationPipelineRunner;
        this.meterRegistry = meterRegistry;
    }

    public void launch(final SseEventPublisher publisher, final UUID userId, final PreparedGenerationStartContext startContext) {
        final GenerationCancellationToken cancellationToken = projectGenerationCancellationService.openSession(startContext.projectId());
        registerCancellationCallbacks(startContext.projectId(), publisher);

        final SecurityContext context = SecurityContextHolder.getContext();
        final Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();
        final Runnable executionTask = () -> {
            SecurityContextHolder.setContext(context);
            if (mdcContextMap != null) {
                MDC.setContextMap(mdcContextMap);
            }
            try {
                cancellationToken.bindThread(Thread.currentThread());
                preparedGenerationPipelineRunner.run(
                        publisher,
                        startContext.projectId(),
                        userId,
                        startContext.creditReservationReference(),
                        startContext.launch(),
                        startContext.ideTarget(),
                        startContext.resolvedHeavyModel(),
                        cancellationToken);
            } finally {
                SecurityContextHolder.clearContext();
                MDC.clear();
            }
        };

        publishRunCreatedAndDispatch(publisher, startContext.projectId(), executionTask, startContext.runCreatedPayload());
    }

    private void dispatchExecutionTask(
            final SseEventPublisher publisher,
            final UUID projectId,
            final Runnable executionTask) {
        try {
            applicationTaskExecutor.execute(executionTask);
        } catch (final TaskRejectedException exception) {
            meterRegistry.counter(
                    "project_generation.generation",
                    "outcome", "failed",
                    "reason", "executor_rejection").increment();
            log.error("Executor rejected prepared project generation task for projectId={}", projectId, exception);
            compensateFailure(projectId, exception, true, GenerationFailureReason.WORKER_FAILURE);
            projectGenerationCancellationService.closeSession(projectId);
            publishError(publisher, "FAILED", "Sistem su anda cok yogun, lutfen daha sonra tekrar deneyin.", exception);
        }
    }

    private void publishRunCreatedAndDispatch(
            final SseEventPublisher publisher,
            final UUID projectId,
            final Runnable executionTask,
            final com.arslanca.orchestrai.business.dtos.projectgeneration.RunCreatedPayload runCreatedPayload) {
        try {
            publisher.sendEvent(SseProgressEvent.runCreated(runCreatedPayload));
            meterRegistry.counter("project_generation.run_created", "outcome", "emitted").increment();
        } catch (final RuntimeException exception) {
            meterRegistry.counter("project_generation.run_created", "outcome", "failed").increment();
            log.debug("RUN_CREATED gonderilemedi, generation iptal isaretlendi: projectId={}, message={}", projectId,
                    exception.getMessage());
            projectGenerationCancellationService.cancelGeneration(projectId, GenerationFailureReason.CLIENT_DISCONNECTED);
        }
        dispatchExecutionTask(publisher, projectId, executionTask);
    }

    private void registerCancellationCallbacks(final UUID projectId, final SseEventPublisher publisher) {
        publisher.onCompletion(() -> projectGenerationCancellationService.cancelGeneration(projectId, GenerationFailureReason.CLIENT_DISCONNECTED));
        publisher.onTimeout(() -> projectGenerationCancellationService.cancelGeneration(projectId, GenerationFailureReason.CLIENT_TIMEOUT));
        publisher.onError(error -> projectGenerationCancellationService.cancelGeneration(projectId, GenerationFailureReason.CLIENT_DISCONNECTED));
    }

    private void publishError(
            final SseEventPublisher publisher,
            final String recoveryReason,
            final String message,
            final Throwable error) {
        try {
            publisher.sendEvent(SseProgressEvent.error(message));
            if (error == null) {
                publisher.complete();
            } else {
                publisher.completeWithError(error);
            }
        } catch (final Exception publishException) {
            log.debug("Error publishing project generation failure {}: {}", recoveryReason, publishException.getMessage());
        }
    }

    private void compensateFailure(
            final UUID projectId,
            final Throwable error,
            final boolean requiresRefund,
            final GenerationFailureReason recoveryReason) {
        try {
            generationFailureCompensationService.compensateFailure(projectId, error, requiresRefund, recoveryReason);
        } catch (final Exception compensationException) {
            log.error("Generation failure compensation also failed: projectId={}", projectId, compensationException);
        }
    }
}
