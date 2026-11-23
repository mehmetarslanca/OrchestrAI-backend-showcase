package com.arslanca.orchestrai.business.generation.runtime;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.ArtifactAssemblyService;
import com.arslanca.orchestrai.business.abstracts.DistributedSemaphoreService;
import com.arslanca.orchestrai.business.abstracts.GenerationFailureCompensationService;
import com.arslanca.orchestrai.business.abstracts.OrchestrationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationCancellationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationFinalizationService;
import com.arslanca.orchestrai.business.abstracts.ScaffoldMaterializationService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.concretes.ProjectStateService;
import com.arslanca.orchestrai.business.concretes.cancellation.GenerationCancellationToken;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationResultPayload;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStage;
import com.arslanca.orchestrai.business.dtos.projectgeneration.OrchestrationResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PreparedGenerationLaunch;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.sse.SseProgressEvent;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.dtos.orchestration.PreviewReuseDisposition;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.arslanca.orchestrai.core.exception.type.ProjectGenerationCancelledException;
import com.arslanca.orchestrai.entity.enums.IdeTarget;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class PreparedGenerationPipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(PreparedGenerationPipelineRunner.class);
    private static final long PING_INTERVAL_SECONDS = 20L;
    private static final int MAX_CONSECUTIVE_PING_FAILURES = 3;
    private static final String PIPELINE_SEMAPHORE_RESOURCE = "active_count";
    private static final String CORRELATION_ID_KEY = "correlationId";

    private final OrchestrationService orchestrationService;
    private final ProjectStateService projectStateService;
    private final TaskExecutor applicationTaskExecutor;
    private final TaskScheduler taskScheduler;
    private final DistributedSemaphoreService distributedSemaphoreService;
    private final GenerationFailureCompensationService generationFailureCompensationService;
    private final ProjectGenerationCancellationService projectGenerationCancellationService;
    private final ProjectGenerationFinalizationService projectGenerationFinalizationService;
    private final PlanningLifecycleAuthority lifecycleAuthority;
    private final ScaffoldMaterializationService scaffoldMaterializationService;
    private final ArtifactAssemblyService artifactAssemblyService;
    private final MeterRegistry meterRegistry;
    private final BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter;

    public PreparedGenerationPipelineRunner(
            final OrchestrationService orchestrationService,
            final ProjectStateService projectStateService,
            final TaskExecutor applicationTaskExecutor,
            final TaskScheduler taskScheduler,
            final DistributedSemaphoreService distributedSemaphoreService,
            final GenerationFailureCompensationService generationFailureCompensationService,
            final ProjectGenerationCancellationService projectGenerationCancellationService,
            final ProjectGenerationFinalizationService projectGenerationFinalizationService,
            final PlanningLifecycleAuthority lifecycleAuthority,
            final ScaffoldMaterializationService scaffoldMaterializationService,
            final ArtifactAssemblyService artifactAssemblyService,
            final MeterRegistry meterRegistry,
            final BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter) {
        this.orchestrationService = orchestrationService;
        this.projectStateService = projectStateService;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.taskScheduler = taskScheduler;
        this.distributedSemaphoreService = distributedSemaphoreService;
        this.generationFailureCompensationService = generationFailureCompensationService;
        this.projectGenerationCancellationService = projectGenerationCancellationService;
        this.projectGenerationFinalizationService = projectGenerationFinalizationService;
        this.lifecycleAuthority = lifecycleAuthority;
        this.scaffoldMaterializationService = scaffoldMaterializationService;
        this.artifactAssemblyService = artifactAssemblyService;
        this.meterRegistry = meterRegistry;
        this.blueprintPlanCompatibilityAdapter = blueprintPlanCompatibilityAdapter;
    }

    public void run(
            final SseEventPublisher publisher,
            final UUID projectId,
            final UUID userId,
            final String creditReservationReference,
            final PreparedGenerationLaunch launch,
            final IdeTarget ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final GenerationCancellationToken cancellationToken) {
        boolean refundEligible = true;
        final String leaseToken = projectId.toString();
        final boolean acquired;
        try {
            acquired = distributedSemaphoreService.tryAcquire(PIPELINE_SEMAPHORE_RESOURCE, leaseToken);
        } catch (final InfrastructureUnavailableException exception) {
            recordGenerationMetric("failed", "semaphore_unavailable");
            log.error("Pipeline semaphore unavailable for projectId={}", projectId, exception);
            compensateFailure(projectId, exception, true, GenerationFailureReason.SEMAPHORE_UNAVAILABLE);
            publishErrorUnlessCancelled(cancellationToken, publisher,
                    "Sistem gecici olarak kullanilamiyor. Lutfen daha sonra tekrar deneyin.", exception);
            return;
        }

        if (!acquired) {
            recordGenerationMetric("failed", "concurrency_limit");
            log.warn("Pipeline concurrency limit reached for prepared projectId={}", projectId);
            compensateFailure(projectId, new IllegalStateException("Pipeline concurrency limit exceeded"), true,
                    GenerationFailureReason.CONCURRENCY_LIMIT);
            publishErrorUnlessCancelled(cancellationToken, publisher,
                    "Sistem su anda cok yogun, lutfen daha sonra tekrar deneyin.", null);
            return;
        }

        final ScheduledFuture<?> pingTask = startPingScheduler(projectId, publisher);
        try {
            cancellationToken.throwIfCancellationRequested();
            projectStateService.markProjectAsProcessing(projectId);
            refundEligible = false;

            final CanonicalBlueprint blueprint;
            final PlanningQualitySummary planningQualitySummary;
            final PlanningRepairOutcome planningRepairOutcome;
            final StarterSelection finalizedStarterSelection = launch.starterSelection();
            if (launch.previewBlueprint() != null) {
                publisher.sendEvent(SseProgressEvent.stageChange(GenerationStage.PREPARING_REVIEW,
                        "Accepted planning preview reused. Artifact generation is starting..."));
                blueprint = launch.previewBlueprint();
                planningQualitySummary = launch.planningQualitySummary();
                planningRepairOutcome = launch.planningRepairOutcome();
            } else {
                publisher.sendEvent(SseProgressEvent.stageChange(GenerationStage.PREPARING_REVIEW,
                        "Starter recommendation approved. Blueprint generation is starting..."));

                final Timer.Sample orchestrationTimer = Timer.start(meterRegistry);
                final OrchestrationResult orchestrationResult;
                final GenerationUsageContext generationUsageContext = new GenerationUsageContext(
                        userId,
                        projectId,
                        launch.preparationId(),
                        launch.previewId(),
                        creditReservationReference,
                        MDC.get(CORRELATION_ID_KEY));
                try {
                    orchestrationResult = orchestrationService.orchestratePrepared(
                            launch.structuredAnalysis(),
                            launch.ideTarget(),
                            resolvedHeavyModel,
                            finalizedStarterSelection,
                            cancellationToken,
                            (stage, message) -> publisher.sendEvent(SseProgressEvent.stageChange(stage, message)),
                            generationUsageContext);
                    orchestrationTimer.stop(meterRegistry.timer(
                            "project_generation.orchestration.duration",
                            "outcome", "success",
                            "starterKey", resolveStarterKey(finalizedStarterSelection)));
                } catch (final ProjectGenerationCancelledException exception) {
                    orchestrationTimer.stop(meterRegistry.timer(
                            "project_generation.orchestration.duration",
                            "outcome", "cancelled",
                            "starterKey", resolveStarterKey(finalizedStarterSelection)));
                    throw exception;
                } catch (final RuntimeException exception) {
                    orchestrationTimer.stop(meterRegistry.timer(
                            "project_generation.orchestration.duration",
                            "outcome", "failed",
                            "starterKey", resolveStarterKey(finalizedStarterSelection)));
                    throw exception;
                }
                blueprint = orchestrationResult.canonicalBlueprint();
                planningQualitySummary = orchestrationResult.planningQualitySummary();
                planningRepairOutcome = orchestrationResult.planningRepairOutcome();
            }

            cancellationToken.throwIfCancellationRequested();
            publisher.sendEvent(SseProgressEvent.stageChange(
                    GenerationStage.MATERIALIZING_STARTER,
                    "Starter scaffold materialize ediliyor..."));

            final var materializedStarter = scaffoldMaterializationService.materialize(
                    finalizedStarterSelection,
                    projectId,
                    cancellationToken::throwIfCancellationRequested);
            cancellationToken.throwIfCancellationRequested();

            if (materializedStarter.degraded()) {
                publisher.sendEvent(SseProgressEvent.progress(
                        GenerationStage.MATERIALIZING_STARTER,
                        "Starter materialization degrade oldu; generation docs-only uyumlu sekilde devam ediyor.",
                        55));
            } else {
                publisher.sendEvent(SseProgressEvent.progress(
                        GenerationStage.MATERIALIZING_STARTER,
                        "Starter scaffold hazirlandi: %d dosya normalize edildi."
                                .formatted(materializedStarter.metadata().fileCount()),
                        55));
            }

            publisher.sendEvent(SseProgressEvent.stageChange(GenerationStage.ASSEMBLING_ARTIFACT,
                    "IDE-native artifact birlestiriliyor..."));
            final BlueprintPlan boundaryBlueprint = blueprintPlanCompatibilityAdapter.toBlueprintPlan(blueprint);
            final DeliveryResult deliveryResult = artifactAssemblyService.assembleArtifact(
                    boundaryBlueprint,
                    materializedStarter,
                    ideTarget);
            cancellationToken.throwIfCancellationRequested();

            publisher.sendEvent(SseProgressEvent.stageChange(GenerationStage.PERSISTING_PROJECT,
                    "Project artifact persist ediliyor..."));
            projectGenerationFinalizationService.finalizeSuccess(
                    projectId,
                    userId,
                    creditReservationReference,
                    deliveryResult,
                    resolvedHeavyModel,
                    finalizedStarterSelection,
                    materializedStarter,
                    planningQualitySummary,
                    planningRepairOutcome);

            publisher.sendEvent(SseProgressEvent.progress(
                    GenerationStage.PERSISTING_PROJECT,
                    "Project artifact persist edildi.",
                    95));
            publisher.sendEvent(SseProgressEvent.result(new GenerationResultPayload(
                    projectId,
                    launch.projectName(),
                    "COMPLETED",
                    true,
                    ideTarget.name().toLowerCase())));
            recordGenerationMetric("success", "prepared_completed");
            projectGenerationCancellationService.closeSession(projectId);
            publisher.complete();
        } catch (final ProjectGenerationCancelledException exception) {
            recordGenerationMetric("cancelled", exception.getCancellationReason());
            log.info("Prepared project generation cancelled: projectId={}, reason={}", projectId,
                    exception.getCancellationReason());
            compensateFailure(projectId, exception, refundEligible, exception.getFailureReason());
        } catch (final Exception exception) {
            recordGenerationMetric("failed", "prepared_pipeline_failure");
            log.error("Prepared project generation pipeline failed: projectId={}", projectId, exception);
            compensateFailure(projectId, exception, true, GenerationFailureReason.PIPELINE_FAILURE);
            publishErrorUnlessCancelled(cancellationToken, publisher,
                    "Uretim sirasinda bir hata olustu. Rezerve kredi durumu: iade edildi",
                    exception);
        } finally {
            distributedSemaphoreService.release(PIPELINE_SEMAPHORE_RESOURCE, leaseToken);
            if (pingTask != null) {
                pingTask.cancel(false);
            }
            projectGenerationCancellationService.closeSession(projectId);
        }
    }

    private ScheduledFuture<?> startPingScheduler(final UUID projectId, final SseEventPublisher publisher) {
        final Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();
        final AtomicInteger consecutivePingFailures = new AtomicInteger(0);
        return taskScheduler.scheduleAtFixedRate(() -> {
            try {
                applicationTaskExecutor.execute(() -> {
                    if (mdcContextMap != null) {
                        MDC.setContextMap(mdcContextMap);
                    }
                    try {
                        publisher.sendEvent(SseProgressEvent.ping());
                        consecutivePingFailures.set(0);
                    } catch (final Exception exception) {
                        final int failureCount = consecutivePingFailures.incrementAndGet();
                        log.debug("PING gonderilemedi (failureCount={}): {}", failureCount, exception.getMessage());
                        if (failureCount >= MAX_CONSECUTIVE_PING_FAILURES) {
                            projectGenerationCancellationService.cancelGeneration(projectId, GenerationFailureReason.PING_FAILED);
                        }
                    } finally {
                        MDC.clear();
                    }
                });
            } catch (final Exception exception) {
                log.debug("PING task baslatilamadi: {}", exception.getMessage());
            }
        }, Duration.ofSeconds(PING_INTERVAL_SECONDS));
    }

    private void publishErrorUnlessCancelled(
            final GenerationCancellationToken cancellationToken,
            final SseEventPublisher publisher,
            final String message,
            final Throwable error) {
        if (cancellationToken.isCancellationRequested()) {
            return;
        }
        publishError(publisher, "FAILED", message, error);
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

    private void recordGenerationMetric(final String outcome, final String reason) {
        meterRegistry.counter(
                "project_generation.generation",
                "outcome", sanitizeMetricTag(outcome),
                "reason", sanitizeMetricTag(reason))
                .increment();
    }

    private String resolveStarterKey(final StarterSelection starterSelection) {
        if (starterSelection == null) {
            return "unknown";
        }
        final String selectedStarterKey = lifecycleAuthority.selectedStarterKeyFor(starterSelection);
        if (lifecycleAuthority.dispositionFor(selectedStarterKey) == PreviewReuseDisposition.REUSE_PREPARATION_SELECTION) {
            return selectedStarterKey;
        }
        return sanitizeMetricTag(selectedStarterKey);
    }

    private String sanitizeMetricTag(final String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }
}
