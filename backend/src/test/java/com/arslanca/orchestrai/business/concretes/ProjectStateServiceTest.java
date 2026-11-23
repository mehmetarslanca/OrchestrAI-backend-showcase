package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifest;
import com.arslanca.orchestrai.business.delivery.manifest.ManifestDocumentEntry;
import com.arslanca.orchestrai.business.delivery.composition.DocumentKind;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactProvenance;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializationMetadata;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectArtifactRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectArtifactEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStateServiceTest {

  @Mock
  private ProjectRepository projectRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ProjectArtifactRepository projectArtifactRepository;

  @InjectMocks
  private ProjectStateService projectStateService;

  private UUID projectId;
  private ProjectEntity projectEntity;
  private ResolvedHeavyModel heavyModel;
  private DeliveryResult deliveryResult;

  private DeliveryManifest manifest() {
    return new DeliveryManifest(
        "2.0",
        "project",
        "cursor",
        "Executive summary",
        "Architecture overview",
        "System context",
        List.of("Use typed seams"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "Starter summary",
        List.of(),
        1,
        List.of(new ManifestDocumentEntry(
            DocumentKind.ARCHITECTURE_OVERVIEW,
            "architect",
            "System Architecture",
            List.of("executive-summary"),
            42,
            "docs/architect.md")));
  }

  @BeforeEach
  void setUp() {
    projectId = UUID.randomUUID();
    projectEntity = instantiateProjectEntity(projectId);

    heavyModel = new ResolvedHeavyModel(
        "model-key", "provider", "family", CredentialSource.PLATFORM, "cred", "concrete-id", "display", false, false,
        "gateway", "runtimeProvider", "runtimeTarget", List.of());
    deliveryResult = new DeliveryResult("project", "cursor", List.of(
        new FileNode("README.md", "hello"),
        new FileNode(".mvn/wrapper/maven-wrapper.jar", Base64.getEncoder().encodeToString("jar-binary".getBytes(StandardCharsets.UTF_8)), "base64", "jar-binary".getBytes(StandardCharsets.UTF_8).length, true)
    ), manifest());

    projectEntity.markAsProcessing();
    when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(projectEntity));
  }

  @Test
  void shouldPersistDocsOnlyFallbackFromStarterSelection() {
    StarterSelection selection = new StarterSelection(
        null, "project", "cursor", null, List.of(), true, "User requested docs");

    projectStateService.updateProjectAsCompleted(projectId, deliveryResult, heavyModel, selection, null);

    Map<String, Object> metadata = readField(projectEntity, "metadata", Map.class);
    assertThat(metadata.get("docsOnlyFallback")).isEqualTo(true);
    assertThat(metadata.get("docsOnlyFallbackReason")).isEqualTo("User requested docs");
    assertThat(metadata.get("docsOnlyRequested")).isEqualTo(true);
    assertThat(metadata.get("docsOnlyRequestedReason")).isEqualTo("User requested docs");
    assertThat(metadata.get("materializationDegraded")).isEqualTo(false);
    assertThat(metadata.get(ProjectStateService.CREDIT_OUTCOME_KEY)).isEqualTo(ProjectStateService.CREDIT_OUTCOME_CAPTURED);
    assertThat(metadata.get(ProjectStateService.TERMINAL_STATE_SOURCE_KEY)).isEqualTo(ProjectStateService.TERMINAL_STATE_SOURCE_FINALIZATION);
    assertThat(metadata.get("artifactSchemaVersion")).isEqualTo("v2");
    assertThat(metadata.get("artifactFileCount")).isEqualTo(2);
    assertThat(metadata.get("artifactAvailable")).isEqualTo(true);
    assertThat(metadata.get("deliveryManifestSchemaVersion")).isEqualTo("2.0");
    assertThat(metadata.containsKey("selectedStarterKey")).isFalse();
  }

  @Test
  void shouldPersistDocsOnlyFallbackWhenMaterializedStarterIsDegraded() {
    StarterCandidate candidate = new StarterCandidate("key", "title", "desc", StarterSourceKind.REPO_PACK, "ref",
        List.of(), "reason");
    StarterSelection selection = new StarterSelection(
        null, "project", "cursor", candidate, List.of(), false, "");
    MaterializedStarter materialized = new MaterializedStarter(
        "key",
        StarterSourceKind.REPO_PACK,
        "ref",
        List.of(),
        new MaterializationMetadata(0, 0L, 100L, List.of("Network error"), true, "Network error"),
        true);

    projectStateService.updateProjectAsCompleted(projectId, deliveryResult, heavyModel, selection, materialized);

    Map<String, Object> metadata = readField(projectEntity, "metadata", Map.class);
    assertThat(metadata.get("docsOnlyFallback")).isEqualTo(true);
    assertThat(metadata.get("docsOnlyFallbackReason")).isEqualTo("Network error");
    assertThat(metadata.get("docsOnlyRequested")).isEqualTo(false);
    assertThat(metadata.get("materializationDegraded")).isEqualTo(true);
    assertThat(metadata.get("materializationDegradeReason")).isEqualTo("Network error");
    assertThat(metadata.get(ProjectStateService.CREDIT_OUTCOME_KEY)).isEqualTo(ProjectStateService.CREDIT_OUTCOME_CAPTURED);
    assertThat(metadata.get("selectedStarterKey")).isEqualTo("key");
    assertThat(metadata.get("artifactFileCount")).isEqualTo(2);
  }

  @Test
  void shouldPersistStarterProvenanceWhenSuccessful() {
    StarterCandidate candidate = new StarterCandidate("my-starter", "My Starter", "desc",
        StarterSourceKind.OFFICIAL_ADAPTER, "my/ref", List.of(), "reason");
    StarterSelection selection = new StarterSelection(
        null, "project", "cursor", candidate, List.of(), false, "");
    MaterializedStarter materialized = new MaterializedStarter("my-starter", StarterSourceKind.OFFICIAL_ADAPTER,
        "my/ref", List.of(), new MaterializationMetadata(0, 0L, 0L, List.of(), false, ""), false);

    projectStateService.updateProjectAsCompleted(projectId, deliveryResult, heavyModel, selection, materialized);

    Map<String, Object> metadata = readField(projectEntity, "metadata", Map.class);
    assertThat(metadata.get("docsOnlyFallback")).isEqualTo(false);
    assertThat(metadata.get("docsOnlyRequested")).isEqualTo(false);
    assertThat(metadata.get("materializationDegraded")).isEqualTo(false);
    assertThat(metadata.get(ProjectStateService.CREDIT_OUTCOME_KEY)).isEqualTo(ProjectStateService.CREDIT_OUTCOME_CAPTURED);
    assertThat(metadata.containsKey("docsOnlyFallbackReason")).isFalse();
    assertThat(metadata.get("selectedStarterKey")).isEqualTo("my-starter");
    assertThat(metadata.get("selectedStarterTitle")).isEqualTo("My Starter");
    assertThat(metadata.get("starterSourceKind")).isEqualTo("official-adapter"); // testing .value()
    assertThat(metadata.get("starterSourceRef")).isEqualTo("my/ref");
    assertThat(metadata.get("artifactTotalBytes")).isEqualTo(15L);
  }

  @Test
  void shouldHandleNullSelectionAndMaterializationGracefully() {
    projectStateService.updateProjectAsCompleted(projectId, deliveryResult, heavyModel, null, null);

    Map<String, Object> metadata = readField(projectEntity, "metadata", Map.class);
    assertThat(metadata.get("docsOnlyFallback")).isEqualTo(false);
    assertThat(metadata.get("docsOnlyRequested")).isEqualTo(false);
    assertThat(metadata.get("materializationDegraded")).isEqualTo(false);
    assertThat(metadata.get(ProjectStateService.CREDIT_OUTCOME_KEY)).isEqualTo(ProjectStateService.CREDIT_OUTCOME_CAPTURED);
    assertThat(metadata.containsKey("selectedStarterKey")).isFalse();
  }

  @Test
  void shouldPersistTypedArtifactEntityMetadata() {
    projectStateService.updateProjectAsCompleted(projectId, deliveryResult, heavyModel, null, null);

    ArgumentCaptor<ProjectArtifactEntity> captor = ArgumentCaptor.forClass(ProjectArtifactEntity.class);
    verify(projectArtifactRepository).save(captor.capture());

    ProjectArtifactEntity savedArtifact = captor.getValue();
    ProjectArtifactProvenance metadata = readField(savedArtifact, "metadata", ProjectArtifactProvenance.class);
    var finalTree = readField(savedArtifact, "finalTree", com.arslanca.orchestrai.business.dtos.project.ProjectArtifactData.class);

    assertThat(finalTree.version()).isEqualTo("v2");
    assertThat(finalTree.tree()).hasSize(2);
    assertThat(finalTree.manifest().schemaVersion()).isEqualTo("2.0");
    assertThat(finalTree.manifest().inventory()).singleElement().extracting(ManifestDocumentEntry::renderedPath).isEqualTo("docs/architect.md");
    assertThat(metadata.artifactSchemaVersion()).isEqualTo("v2");
    assertThat(metadata.deliveryManifestSchemaVersion()).isEqualTo("2.0");
    assertThat(metadata.artifactFileCount()).isEqualTo(2);
    assertThat(metadata.artifactTotalBytes()).isEqualTo(15L);
    assertThat(metadata.artifactAvailable()).isTrue();
    assertThat(metadata.deliveryTarget()).isEqualTo("cursor");
    assertThat(metadata.planningQualitySummary().qualityVerdict().value()).isEqualTo("reject");
    assertThat(metadata.resolvedHeavyModel()).isNotNull();
    assertThat(metadata.resolvedHeavyModel().modelKey()).isEqualTo("model-key");
  }

  @Test
  void shouldPersistCancellationIntentOnlyWhileProjectIsActive() {
    boolean updated = projectStateService.markCancellationRequested(projectId, GenerationFailureReason.CLIENT_TIMEOUT);

    assertThat(updated).isTrue();
    Map<String, Object> metadata = readField(projectEntity, "metadata", Map.class);
    assertThat(metadata.get(ProjectStateService.CANCEL_REQUESTED_KEY)).isEqualTo(true);
    assertThat(metadata.get(ProjectStateService.CANCEL_REQUESTED_REASON_KEY)).isEqualTo(GenerationFailureReason.CLIENT_TIMEOUT.metadataValue());
  }

  @SuppressWarnings("unchecked")
  private <T> T readField(final Object target, final String fieldName, final Class<T> fieldType) {
    try {
      final Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return (T) field.get(target);
    } catch (final ReflectiveOperationException exception) {
      throw new AssertionError("Failed to read field: " + fieldName, exception);
    }
  }

  private ProjectEntity instantiateProjectEntity(final UUID id) {
    try {
      final var constructor = ProjectEntity.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      final ProjectEntity entity = constructor.newInstance();
      final Field idField = ProjectEntity.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(entity, id);
      return entity;
    } catch (final ReflectiveOperationException exception) {
      throw new AssertionError("Failed to instantiate ProjectEntity", exception);
    }
  }
}
