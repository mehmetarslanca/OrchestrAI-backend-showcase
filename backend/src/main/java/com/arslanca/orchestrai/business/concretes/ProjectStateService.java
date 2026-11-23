package com.arslanca.orchestrai.business.concretes;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactData;
import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactProvenance;
import com.arslanca.orchestrai.business.dtos.project.ResolvedHeavyModelSummary;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectArtifactRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectArtifactEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.enums.IdeTarget;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectStateService {

  public static final String CREDIT_RESERVATION_REFERENCE_KEY = "creditReservationReference";
  public static final String RECOVERY_REASON_KEY = "recoveryReason";
  public static final String CANCEL_REQUESTED_KEY = "cancelRequested";
  public static final String CANCEL_REQUESTED_REASON_KEY = "cancelRequestedReason";
  public static final String CANCEL_REQUESTED_AT_KEY = "cancelRequestedAt";
  public static final String TERMINAL_STATE_AT_KEY = "terminalStateAt";
  public static final String TERMINAL_STATE_SOURCE_KEY = "terminalStateSource";
  public static final String CREDIT_OUTCOME_KEY = "creditOutcome";
  public static final String STALE_PROJECT_STATUS_KEY = "staleProjectStatus";

  public static final String TERMINAL_STATE_SOURCE_FINALIZATION = "FINALIZATION";
  public static final String TERMINAL_STATE_SOURCE_COMPENSATION = "COMPENSATION";
  public static final String TERMINAL_STATE_SOURCE_RECOVERY_JOB = "RECOVERY_JOB";

  public static final String CREDIT_OUTCOME_CAPTURED = "CAPTURED";
  public static final String CREDIT_OUTCOME_RELEASED = "RELEASED";
  public static final String CREDIT_OUTCOME_REFUNDED = "REFUNDED";
  private static final Logger log = LoggerFactory.getLogger(ProjectStateService.class);

  private final ProjectRepository projectRepository;
  private final UserRepository userRepository;
  private final ProjectArtifactRepository projectArtifactRepository;

  @Transactional
  public ProjectEntity createProjectRecord(
      final UUID userId,
      final ProjectRequest request,
      final IdeTarget ideTarget,
      final ResolvedHeavyModel resolvedHeavyModel,
      final String creditReservationReference) {

    final var user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı: userId=" + userId));

    final var project = ProjectEntity.builder()
        .user(user)
        .projectName(request.projectName())
        .ideTarget(ideTarget)
        .originalPrompt(request.prompt())
        .metadata(buildInitialMetadata(resolvedHeavyModel, creditReservationReference))
        .build();

    final var savedProject = projectRepository.save(project);
    log.info("Project record created: projectId={}, status=PENDING", savedProject.getId());
    return savedProject;
  }

  @Transactional
  public void markProjectAsProcessing(final UUID projectId) {
    final var managedProject = projectRepository.findByIdForUpdate(projectId).orElseThrow();
    managedProject.markAsProcessing();
    log.debug("Project transitioned to PROCESSING: projectId={}", projectId);
  }

  @Transactional
  public void updateProjectAsCompleted(
      final UUID projectId,
      final DeliveryResult result,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final MaterializedStarter materializedStarter) {
    updateProjectAsCompleted(
        projectId,
        result,
        resolvedHeavyModel,
        starterSelection,
        materializedStarter,
        null,
        null);
  }

  @Transactional
  public void updateProjectAsCompleted(
      final UUID projectId,
      final DeliveryResult result,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final MaterializedStarter materializedStarter,
      final PlanningQualitySummary planningQualitySummary,
      final PlanningRepairOutcome planningRepairOutcome) {
    final var managedProject = projectRepository.findByIdForUpdate(projectId).orElseThrow();
    final OffsetDateTime completedAt = OffsetDateTime.now();
    final ProjectArtifactProvenance artifactProvenance = buildArtifactProvenance(
        result,
        resolvedHeavyModel,
        starterSelection,
        materializedStarter,
        planningQualitySummary,
        planningRepairOutcome,
        completedAt);
    final Map<String, Object> completionMetadata = buildProjectCompletionMetadata(artifactProvenance);

    managedProject.markAsCompleted(completionMetadata);

    final var artifact = ProjectArtifactEntity.builder()
        .project(managedProject)
        .finalTree(new ProjectArtifactData(ProjectArtifactData.CURRENT_VERSION, result.tree(), result.manifest()))
        .metadata(artifactProvenance)
        .build();
    projectArtifactRepository.save(artifact);

    log.info("Project marked as COMPLETED: projectId={}", projectId);
  }

  private String resolveMaterializationDegradeReason(final MaterializedStarter materializedStarter) {
    if (materializedStarter == null || !materializedStarter.degraded()) {
      return null;
    }

    if (materializedStarter.metadata() != null && materializedStarter.metadata().degradeReason() != null
        && !materializedStarter.metadata().degradeReason().isBlank()) {
      return materializedStarter.metadata().degradeReason();
    }

    if (materializedStarter.metadata() != null && !materializedStarter.metadata().warnings().isEmpty()) {
      return String.join("; ", materializedStarter.metadata().warnings());
    }

    return "Starter materialization degraded";
  }

  private ProjectArtifactProvenance buildArtifactProvenance(
      final DeliveryResult result,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final MaterializedStarter materializedStarter,
      final PlanningQualitySummary planningQualitySummary,
      final PlanningRepairOutcome planningRepairOutcome,
      final OffsetDateTime completedAt) {
    final boolean docsOnlyRequested = starterSelection != null && starterSelection.docsOnlyFallback();
    final String docsOnlyRequestedReason = docsOnlyRequested ? starterSelection.docsOnlyFallbackReason() : null;
    final boolean materializationDegraded = materializedStarter != null && materializedStarter.degraded();
    final String materializationDegradeReason = resolveMaterializationDegradeReason(materializedStarter);
    final boolean docsOnlyFallback = docsOnlyRequested || materializationDegraded;
    final String docsOnlyFallbackReason = docsOnlyRequested ? docsOnlyRequestedReason : materializationDegradeReason;
    final int artifactFileCount = result.tree().size();
    final long artifactTotalBytes = result.tree().stream().mapToLong(this::resolveNodeSize).sum();
    final boolean artifactAvailable = artifactFileCount > 0;

    String selectedStarterKey = null;
    String selectedStarterTitle = null;
    String starterSourceKind = null;
    String starterSourceRef = null;

    if (starterSelection != null && starterSelection.recommendedStarter() != null) {
      selectedStarterKey = starterSelection.recommendedStarter().key();
      selectedStarterTitle = starterSelection.recommendedStarter().title();
      starterSourceKind = starterSelection.recommendedStarter().sourceKind() == null
          ? null
          : starterSelection.recommendedStarter().sourceKind().value();
      starterSourceRef = starterSelection.recommendedStarter().sourceRef();
    }

    return new ProjectArtifactProvenance(
        ProjectArtifactData.CURRENT_VERSION,
        buildResolvedHeavyModelSummary(resolvedHeavyModel),
        selectedStarterKey,
        selectedStarterTitle,
        starterSourceKind,
        starterSourceRef,
        docsOnlyRequested,
        docsOnlyRequestedReason,
        materializationDegraded,
        materializationDegradeReason,
        docsOnlyFallback,
        docsOnlyFallbackReason,
        artifactFileCount,
        artifactTotalBytes,
        artifactAvailable,
        result.ideTarget(),
        result.ideTarget(),
        result.ideTarget(),
        result.manifest().schemaVersion(),
        planningQualitySummary,
        planningRepairOutcome,
        completedAt.toString(),
        CREDIT_OUTCOME_CAPTURED,
        completedAt.toString(),
        TERMINAL_STATE_SOURCE_FINALIZATION);
  }

  private Map<String, Object> buildProjectCompletionMetadata(final ProjectArtifactProvenance artifactProvenance) {
    final Map<String, Object> metadata = new LinkedHashMap<>();

    if (artifactProvenance.resolvedHeavyModel() != null) {
      metadata.put("resolvedHeavyModel", toResolvedHeavyModelMetadata(artifactProvenance.resolvedHeavyModel()));
    }

    metadata.put("docsOnlyRequested", artifactProvenance.docsOnlyRequested());
    putIfPresent(metadata, "docsOnlyRequestedReason", artifactProvenance.docsOnlyRequestedReason());

    metadata.put("materializationDegraded", artifactProvenance.materializationDegraded());
    putIfPresent(metadata, "materializationDegradeReason", artifactProvenance.materializationDegradeReason());

    metadata.put("docsOnlyFallback", artifactProvenance.docsOnlyFallback());
    putIfPresent(metadata, "docsOnlyFallbackReason", artifactProvenance.docsOnlyFallbackReason());

    putIfPresent(metadata, "selectedStarterKey", artifactProvenance.selectedStarterKey());
    putIfPresent(metadata, "selectedStarterTitle", artifactProvenance.selectedStarterTitle());
    putIfPresent(metadata, "starterSourceKind", artifactProvenance.starterSourceKind());
    putIfPresent(metadata, "starterSourceRef", artifactProvenance.starterSourceRef());

    metadata.put("artifactSchemaVersion", artifactProvenance.artifactSchemaVersion());
    metadata.put("artifactFileCount", artifactProvenance.artifactFileCount());
    metadata.put("artifactTotalBytes", artifactProvenance.artifactTotalBytes());
    metadata.put("artifactAvailable", artifactProvenance.artifactAvailable());
    metadata.put("ideTarget", artifactProvenance.ideTarget());
    metadata.put("deliveryTarget", artifactProvenance.deliveryTarget());
    metadata.put("deliveryStrategy", artifactProvenance.deliveryStrategy());
    putIfPresent(metadata, "deliveryManifestSchemaVersion", artifactProvenance.deliveryManifestSchemaVersion());
    metadata.put("planningQualitySummary",
        PlanningDecisionPayloadMapper.toLegacyQualitySummary(artifactProvenance.planningQualitySummary()));
    metadata.put("planningRepairOutcome",
        PlanningDecisionPayloadMapper.toLegacyRepairStatus(artifactProvenance.planningRepairOutcome()));
    metadata.put("generationCompletionTimestamp", artifactProvenance.generationCompletedAt());
    metadata.put(CREDIT_OUTCOME_KEY, artifactProvenance.creditOutcome());
    metadata.put(TERMINAL_STATE_AT_KEY, artifactProvenance.terminalStateAt());
    metadata.put(TERMINAL_STATE_SOURCE_KEY, artifactProvenance.terminalStateSource());

    // Legacy compatibility for any callers still reading the previous key.
    metadata.put("fileCount", artifactProvenance.artifactFileCount());

    return metadata;
  }

  private ResolvedHeavyModelSummary buildResolvedHeavyModelSummary(final ResolvedHeavyModel resolvedHeavyModel) {
    if (resolvedHeavyModel == null) {
      return null;
    }

    return new ResolvedHeavyModelSummary(
        resolvedHeavyModel.modelKey(),
        resolvedHeavyModel.provider(),
        resolvedHeavyModel.family(),
        resolvedHeavyModel.source().name(),
        resolvedHeavyModel.source().name(),
        resolvedHeavyModel.credentialOwner(),
        resolvedHeavyModel.concreteModelId(),
        resolvedHeavyModel.displayName(),
        resolvedHeavyModel.testOnly(),
        resolvedHeavyModel.emulated(),
        resolvedHeavyModel.gateway(),
        resolvedHeavyModel.runtimeProvider(),
        resolvedHeavyModel.runtimeTarget(),
        resolvedHeavyModel.badges());
  }

  private Map<String, Object> toResolvedHeavyModelMetadata(final ResolvedHeavyModelSummary summary) {
    final Map<String, Object> resolvedHeavyModelMetadata = new LinkedHashMap<>();
    resolvedHeavyModelMetadata.put("modelKey", summary.modelKey());
    resolvedHeavyModelMetadata.put("provider", summary.provider());
    resolvedHeavyModelMetadata.put("family", summary.family());
    resolvedHeavyModelMetadata.put("source", summary.source());
    resolvedHeavyModelMetadata.put("authSource", summary.authSource());
    resolvedHeavyModelMetadata.put("credentialOwner", summary.credentialOwner());
    resolvedHeavyModelMetadata.put("concreteModelId", summary.concreteModelId());
    resolvedHeavyModelMetadata.put("displayName", summary.displayName());
    resolvedHeavyModelMetadata.put("testOnly", summary.testOnly());
    resolvedHeavyModelMetadata.put("emulated", summary.emulated());
    resolvedHeavyModelMetadata.put("gateway", summary.gateway());
    resolvedHeavyModelMetadata.put("runtimeProvider", summary.runtimeProvider());
    resolvedHeavyModelMetadata.put("runtimeTarget", summary.runtimeTarget());
    resolvedHeavyModelMetadata.put("badges", summary.badges());
    return resolvedHeavyModelMetadata;
  }

  private void putIfPresent(final Map<String, Object> metadata, final String key, final String value) {
    if (value != null && !value.isBlank()) {
      metadata.put(key, value);
    }
  }

  private long resolveNodeSize(final FileNode node) {
    if (node == null) {
      return 0L;
    }
    if (node.sizeBytes() > 0L) {
      return node.sizeBytes();
    }
    if (!node.binary()) {
      return node.content() == null ? 0L : node.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
    if (FileNode.BASE64.equals(node.contentEncoding()) && node.content() != null && !node.content().isEmpty()) {
      try {
        return Base64.getDecoder().decode(node.content()).length;
      } catch (IllegalArgumentException ignored) {
        return 0L;
      }
    }
    return 0L;
  }

  @Transactional
  public void updateProjectAsFailed(
      final UUID projectId,
      final Throwable error,
      final ResolvedHeavyModel resolvedHeavyModel) {
    final var managedProject = projectRepository.findByIdForUpdate(projectId).orElseThrow();
    final Map<String, Object> failureMetadata = new LinkedHashMap<>(
        buildResolvedHeavyModelMetadata(resolvedHeavyModel));
    failureMetadata.put("error", String.valueOf(error.getMessage()));
    failureMetadata.put("errorType", error.getClass().getSimpleName());
    managedProject.markAsFailed(failureMetadata);
    log.warn("Project marked as FAILED: projectId={}", projectId);
  }

  @Transactional
  public boolean markCancellationRequested(final UUID projectId, final GenerationFailureReason reason) {
    final var managedProjectOptional = projectRepository.findByIdForUpdate(projectId);
    if (managedProjectOptional.isEmpty()) {
      return false;
    }

    final var managedProject = managedProjectOptional.get();
    if (!managedProject.isActive()) {
      return false;
    }

    managedProject.appendMetadata(Map.of(
        CANCEL_REQUESTED_KEY, true,
        CANCEL_REQUESTED_REASON_KEY, reason.metadataValue(),
        CANCEL_REQUESTED_AT_KEY, OffsetDateTime.now().toString()));
    projectRepository.save(managedProject);
    log.info("Project cancellation requested: projectId={}, reason={}", projectId, reason.metadataValue());
    return true;
  }

  @Transactional
  public void appendProjectMetadata(final UUID projectId, final Map<String, Object> metadataUpdate) {
    final var managedProject = projectRepository.findByIdForUpdate(projectId).orElseThrow();
    managedProject.appendMetadata(metadataUpdate);
    projectRepository.save(managedProject);
  }

  private Map<String, Object> buildResolvedHeavyModelMetadata(final ResolvedHeavyModel resolvedHeavyModel) {
    final Map<String, Object> metadata = new LinkedHashMap<>();
    final ResolvedHeavyModelSummary summary = buildResolvedHeavyModelSummary(resolvedHeavyModel);
    if (summary != null) {
      metadata.put("resolvedHeavyModel", toResolvedHeavyModelMetadata(summary));
    }
    return metadata;
  }

  private Map<String, Object> buildInitialMetadata(
      final ResolvedHeavyModel resolvedHeavyModel,
      final String creditReservationReference) {
    final Map<String, Object> metadata = new LinkedHashMap<>(buildResolvedHeavyModelMetadata(resolvedHeavyModel));
    metadata.put(CREDIT_RESERVATION_REFERENCE_KEY, creditReservationReference);
    return metadata;
  }
}
