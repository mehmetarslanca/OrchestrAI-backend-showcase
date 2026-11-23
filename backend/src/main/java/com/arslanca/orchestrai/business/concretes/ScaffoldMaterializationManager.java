package com.arslanca.orchestrai.business.concretes;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.arslanca.orchestrai.business.abstracts.ScaffoldMaterializationService;
import com.arslanca.orchestrai.business.abstracts.StarterCatalogService;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedFileNode;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializationMetadata;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterManifest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.core.config.ProjectGenerationWorkerProperties;

import com.arslanca.orchestrai.core.config.projectgeneration.ProjectGenerationFeatureProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class ScaffoldMaterializationManager implements ScaffoldMaterializationService {

  static final String INTERNAL_SECRET_HEADER = "X-Orchestrai-Worker-Secret";
  static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  private static final Logger log = LoggerFactory.getLogger(ScaffoldMaterializationManager.class);

  private final StarterCatalogService starterCatalogService;
  private final RestClient workerRestClient;
  private final ProjectGenerationWorkerProperties workerProperties;
  private final ProjectGenerationFeatureProperties featureProperties;
  private final MeterRegistry meterRegistry;

  public ScaffoldMaterializationManager(
      final StarterCatalogService starterCatalogService,
      final RestClient.Builder workerRestClientBuilder,
      final ProjectGenerationWorkerProperties workerProperties,
      final ProjectGenerationFeatureProperties featureProperties,
      final MeterRegistry meterRegistry) {
    this.starterCatalogService = starterCatalogService;
    this.workerProperties = workerProperties;
    this.featureProperties = featureProperties;
    this.meterRegistry = meterRegistry;
    this.workerRestClient = workerRestClientBuilder
        .baseUrl(workerProperties.getBaseUrl())
        .defaultHeader(INTERNAL_SECRET_HEADER, workerProperties.getInternalSecret())
        .build();
  }

  @Override
  public MaterializedStarter materialize(
      final StarterSelection starterSelection,
      final UUID projectId,
      final Runnable cancellationProbe) {
    final Instant startedAt = Instant.now();
    final Timer.Sample timer = Timer.start(meterRegistry);
    final UUID requestId = UUID.randomUUID();
    final Runnable safeCancellationProbe = cancellationProbe == null ? () -> {
    } : cancellationProbe;

    Objects.requireNonNull(starterSelection, "starterSelection cannot be null");
    Objects.requireNonNull(projectId, "projectId cannot be null");

    if (starterSelection.docsOnlyFallback() || starterSelection.recommendedStarter() == null) {
      if (!featureProperties.isDocsOnlyFallbackEnabled()) {
        meterRegistry.counter("project_generation.worker.rejection", "reason", "docs_only_fallback_disabled")
            .increment();
        throw new IllegalStateException("Docs-only fallback is disabled for unmatched starter selection.");
      }
      meterRegistry.counter("project_generation.fallback", "reason", "docs_only_requested").increment();
      return degradeToDocsOnly(starterSelection, startedAt, timer, starterSelection.docsOnlyFallbackReason());
    }

    // 1. Feature Flag Enforcement
    if (!featureProperties.isWorkerEnabled()) {
      if (!featureProperties.isDocsOnlyFallbackEnabled()) {
        meterRegistry.counter("project_generation.worker.rejection", "reason", "worker_disabled").increment();
        throw new IllegalStateException("Worker materialization is disabled and docs-only fallback is also disabled.");
      }
      // Fallback forced
      meterRegistry.counter("project_generation.fallback", "reason", "worker_disabled").increment();
      return degradeToDocsOnly(starterSelection, startedAt, timer, "Worker disabled by feature flag");
    }

    final String starterKey = starterSelection.recommendedStarter().key();

    try {
      if (!workerProperties.hasConfiguredInternalSecret()) {
        if (!featureProperties.isDocsOnlyFallbackEnabled()) {
          meterRegistry.counter("project_generation.worker.rejection", "reason", "worker_secret_missing").increment();
          throw new IllegalStateException("Worker internal secret is not configured.");
        }
        meterRegistry.counter("project_generation.fallback", "reason", "worker_secret_missing").increment();
        return degradeToDocsOnly(starterSelection, startedAt, timer, "Worker internal secret is not configured");
      }

      final StarterManifest manifest = starterCatalogService.findByKey(starterKey)
          .orElseThrow(() -> new IllegalStateException("Starter manifest not found for key: " + starterKey));

      if (!manifest.isSelectable()) {
        throw new IllegalStateException("Starter manifest is disabled and cannot be materialized: " + starterKey);
      }

      // If official spring initializr is disabled
      if ("spring-initializr".equals(manifest.sourceRef()) && !featureProperties.isOfficialSpringInitializrEnabled()) {
        if (!featureProperties.isDocsOnlyFallbackEnabled()) {
          meterRegistry.counter("project_generation.worker.rejection", "reason", "spring_initializr_disabled")
              .increment();
          throw new IllegalStateException("Spring Initializr worker is disabled and docs-only fallback is disabled.");
        }
        meterRegistry.counter("project_generation.fallback", "reason", "spring_disabled").increment();
        return degradeToDocsOnly(starterSelection, startedAt, timer, "Spring Initializr worker is disabled");
      }

      meterRegistry.counter("project_generation.starter.selection", "starterKey", starterKey).increment();

      safeCancellationProbe.run();

      final MaterializeStarterRequest request = new MaterializeStarterRequest(
          projectId,
          starterSelection.preparationId(),
          requestId,
          starterSelection.projectName(),
          manifest.key(),
          manifest.sourceKind().value(),
          manifest.sourceRef(),
          manifest.family(),
          manifest.stackTags(),
          manifest.parameterSchema());

      final String correlationId = MDC.get("correlationId");
      log.info(
          "Requesting worker materialization: correlationId={}, projectId={}, preparationId={}, requestId={}, starterKey={}, sourceKind={}, sourceRef={}",
          correlationId,
          projectId,
          starterSelection.preparationId(),
          requestId,
          manifest.key(),
          manifest.sourceKind().value(),
          manifest.sourceRef());

      final RestClient.RequestBodySpec requestSpec = workerRestClient.post()
          .uri("/api/v1/materialize");
      if (correlationId != null && !correlationId.isBlank()) {
        requestSpec.header(CORRELATION_ID_HEADER, correlationId);
      }

      final MaterializedStarterResponse response = requestSpec
          .body(request)
          .retrieve()
          .body(MaterializedStarterResponse.class);

      if (response == null) {
        throw new IllegalStateException("Worker returned an empty materialization response");
      }

      safeCancellationProbe.run();

      timer.stop(meterRegistry.timer("project_generation.worker.duration", "status", "success"));
      log.info(
          "Worker materialization completed: correlationId={}, projectId={}, requestId={}, starterKey={}, fileCount={}, totalBytes={}, degraded={}",
          correlationId,
          projectId,
          requestId,
          response.starterKey(),
          response.metadata() == null ? 0 : response.metadata().fileCount(),
          response.metadata() == null ? 0L : response.metadata().totalBytes(),
          response.metadata() != null && response.metadata().degraded());

      return new MaterializedStarter(
          response.starterKey(),
          StarterSourceKind.fromValue(response.sourceKind()),
          response.sourceRef(),
          Optional.ofNullable(response.tree()).orElse(List.of()).stream()
              .map(file -> new MaterializedFileNode(
                  file.path(),
                  file.content(),
                  file.contentEncoding(),
                  file.sizeBytes(),
                  file.binary()))
              .toList(),
          new MaterializationMetadata(
              response.metadata() == null ? 0 : response.metadata().fileCount(),
              response.metadata() == null ? 0L : response.metadata().totalBytes(),
              response.metadata() == null
                  ? Duration.between(startedAt, Instant.now()).toMillis()
                  : response.metadata().durationMs(),
              response.metadata() == null ? List.of() : response.metadata().warnings(),
              response.metadata() != null && response.metadata().degraded(),
              response.metadata() == null ? "" : response.metadata().degradeReason()),
          response.metadata() != null && response.metadata().degraded());
    } catch (final RestClientException | IllegalStateException exception) {
      timer.stop(meterRegistry.timer("project_generation.worker.duration", "status", "failure"));
      if (!featureProperties.isDocsOnlyFallbackEnabled()) {
        meterRegistry.counter("project_generation.worker.failure", "reason", "worker_error_without_fallback")
            .increment();
        throw new IllegalStateException("Worker materialization failed and docs-only fallback is disabled", exception);
      }
      meterRegistry.counter("project_generation.fallback", "reason", "worker_error").increment();
      log.warn("Starter materialization degraded for projectId={}, starterKey={}: {}", projectId, starterKey,
          exception.getMessage());
      return degradeToDocsOnly(starterSelection, startedAt, timer,
          "Starter materialization degraded: " + exception.getMessage());
    }
  }

  private MaterializedStarter degradeToDocsOnly(StarterSelection starterSelection, Instant startedAt,
      Timer.Sample timer, String reason) {
    timer.stop(meterRegistry.timer("project_generation.worker.duration", "status", "degraded"));
    return MaterializedStarter.degraded(
        fallbackStarterKey(starterSelection),
        fallbackSourceKind(starterSelection),
        fallbackSourceRef(starterSelection),
        Duration.between(startedAt, Instant.now()).toMillis(),
        reason);
  }

  private String fallbackStarterKey(final StarterSelection starterSelection) {
    if (starterSelection.recommendedStarter() != null) {
      return starterSelection.recommendedStarter().key();
    }
    return "docs-only";
  }

  private StarterSourceKind fallbackSourceKind(final StarterSelection starterSelection) {
    if (starterSelection.recommendedStarter() != null) {
      return starterSelection.recommendedStarter().sourceKind();
    }
    return StarterSourceKind.REPO_PACK;
  }

  private String fallbackSourceRef(final StarterSelection starterSelection) {
    if (starterSelection.recommendedStarter() != null) {
      return starterSelection.recommendedStarter().sourceRef();
    }
    return "docs-only";
  }

  private record MaterializeStarterRequest(
      UUID projectId,
      UUID preparationId,
      UUID requestId,
      String projectName,
      String starterKey,
      String sourceKind,
      String sourceRef,
      String family,
      List<String> stackTags,
      Map<String, Object> parameterSchema) {
  }

  private record MaterializedStarterResponse(
      String starterKey,
      String sourceKind,
      String sourceRef,
      List<WorkerFileNode> tree,
      WorkerMetadata metadata) {
  }

  private record WorkerFileNode(
      String path,
      String content,
      String contentEncoding,
      long sizeBytes,
      boolean binary) {
  }

  private record WorkerMetadata(
      int fileCount,
      long totalBytes,
      long durationMs,
      List<String> warnings,
      boolean degraded,
      String degradeReason) {
  }
}
