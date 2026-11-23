package com.arslanca.orchestrai.business.concretes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.arslanca.orchestrai.business.abstracts.PreparedProjectGenerationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationCancellationService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.business.generation.runtime.PreparedGenerationLauncher;
import com.arslanca.orchestrai.business.generation.runtime.PreparedGenerationStartContext;
import com.arslanca.orchestrai.business.generation.runtime.PreparedGenerationStartContextResolver;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.planning.lifecycle.LifecycleViolationException;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.exception.type.PreparationStateException;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class PreparedProjectGenerationManager implements PreparedProjectGenerationService {

  private static final Logger log = LoggerFactory.getLogger(PreparedProjectGenerationManager.class);
  private static final String MDC_USER_ID = "userId";
  private static final String MDC_PREPARATION_ID = "preparationId";
  private static final String MDC_PROJECT_ID = "projectId";
  private static final String MDC_STARTER_KEY = "starterKey";
  private static final String MDC_RESOLVED_MODEL = "resolvedModel";

  private final ProjectRepository projectRepository;
  private final PreparedGenerationStartContextResolver preparedGenerationStartContextResolver;
  private final PreparedGenerationLauncher preparedGenerationLauncher;
  private final ProjectGenerationCancellationService projectGenerationCancellationService;
  private final MeterRegistry meterRegistry;

  public PreparedProjectGenerationManager(
      final ProjectRepository projectRepository,
      final PreparedGenerationStartContextResolver preparedGenerationStartContextResolver,
      final PreparedGenerationLauncher preparedGenerationLauncher,
      final ProjectGenerationCancellationService projectGenerationCancellationService,
      final MeterRegistry meterRegistry) {
    this.projectRepository = projectRepository;
    this.preparedGenerationStartContextResolver = preparedGenerationStartContextResolver;
    this.preparedGenerationLauncher = preparedGenerationLauncher;
    this.projectGenerationCancellationService = projectGenerationCancellationService;
    this.meterRegistry = meterRegistry;
  }

  @Override
  @Transactional(noRollbackFor = {
      PreparationStateException.class,
      IllegalArgumentException.class,
      ResourceNotFoundException.class
  })
  public UUID startGeneration(final UUID userId, final GenerationStartRequest request,
      final SseEventPublisher publisher) {
    return startGenerationWithOverride(userId, request, null, null, publisher);
  }

  @Override
  @Transactional(noRollbackFor = {
      PreparationStateException.class,
      IllegalArgumentException.class,
      ResourceNotFoundException.class
  })
  public UUID startGenerationWithOverride(
      final UUID userId,
      final GenerationStartRequest request,
      final String heavyModelKeyOverride,
      final CredentialSource heavyModelAuthSourceOverride,
      final SseEventPublisher publisher) {
    recordGenerationMetric("requested", "prepared_start");
    recordStartMetric("requested", "request_received");
    final Map<String, String> previousMdcContext = MDC.getCopyOfContextMap();

    try {
      putMdcValue(MDC_USER_ID, userId);
      final PreparedGenerationStartContext startContext = preparedGenerationStartContextResolver.resolveStart(
          userId,
          request,
          heavyModelKeyOverride,
          heavyModelAuthSourceOverride);
      putMdcValue(MDC_PREPARATION_ID, startContext.launch().preparationId());
      putMdcValue(MDC_PROJECT_ID, startContext.projectId());
      putMdcValue(MDC_STARTER_KEY, startContext.selectedStarterKey());
      putMdcValue(MDC_RESOLVED_MODEL, startContext.resolvedHeavyModel().modelKey());

      log.info(
          "Prepared generation accepted: userId={}, preparationId={}, projectId={}, starterKey={}, resolvedModel={}",
          userId,
          startContext.launch().preparationId(),
          startContext.projectId(),
          startContext.selectedStarterKey(),
          startContext.resolvedHeavyModel().modelKey());
      recordStartMetric("accepted", "start_dispatched");

      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            preparedGenerationLauncher.launch(publisher, userId, startContext);
          }
        });
        return startContext.projectId();
      }

      preparedGenerationLauncher.launch(publisher, userId, startContext);
      return startContext.projectId();
    } catch (final LifecycleViolationException exception) {
      recordStartMetric("rejected", exception.reason().metricTag());
      throw exception;
    } catch (final ResourceNotFoundException exception) {
      recordStartMetric("rejected", "preparation_not_found");
      throw exception;
    } catch (final PreparationStateException | IllegalArgumentException exception) {
      throw exception;
    } catch (final RuntimeException exception) {
      recordStartMetric("failed", "start_initialization_failed");
      throw exception;
    } finally {
      restoreMdcContext(previousMdcContext);
    }
  }

  @Override
  @Transactional
  public void cancelGeneration(final UUID userId, final UUID projectId) {
    projectRepository.findById(projectId)
        .filter(project -> project.getUser().getId().equals(userId))
        .orElseThrow(() -> ResourceNotFoundException.forProject(projectId));

    projectGenerationCancellationService.cancelGeneration(projectId, GenerationFailureReason.USER_CANCELLED);
  }

  private void recordGenerationMetric(final String outcome, final String reason) {
    meterRegistry.counter(
        "project_generation.generation",
        "outcome", sanitizeMetricTag(outcome),
        "reason", sanitizeMetricTag(reason))
        .increment();
  }

  private void recordStartMetric(final String outcome, final String reason) {
    meterRegistry.counter(
        "project_generation.start.request",
        "outcome", sanitizeMetricTag(outcome),
        "reason", sanitizeMetricTag(reason))
        .increment();
  }

  private void putMdcValue(final String key, final UUID value) {
    if (value != null) {
      MDC.put(key, value.toString());
    }
  }

  private void putMdcValue(final String key, final String value) {
    if (value != null && !value.isBlank()) {
      MDC.put(key, value);
    }
  }

  private void restoreMdcContext(final Map<String, String> previousContext) {
    if (previousContext == null || previousContext.isEmpty()) {
      MDC.clear();
      return;
    }
    MDC.setContextMap(new HashMap<>(previousContext));
  }

  private String sanitizeMetricTag(final String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
  }
}
