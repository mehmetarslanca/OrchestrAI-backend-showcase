package com.arslanca.orchestrai.entity.concretes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "project_generation_preparations")
public class ProjectGenerationPreparationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "chat_id")
  private UUID chatId;

  @Column(name = "chat_message_count")
  private Long chatMessageCount;

  @Column(name = "source_channel", nullable = false, length = 20)
  private String sourceChannel;

  @Column(name = "project_name", nullable = false, length = 200)
  private String projectName;

  @Column(name = "ide_target", nullable = false, length = 30)
  private String ideTarget;

  @Column(name = "prompt_summary", nullable = false, columnDefinition = "TEXT")
  private String promptSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "structured_analysis", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> structuredAnalysis;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "starter_selection", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> starterSelection;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ProjectGenerationPreparationStatus status = ProjectGenerationPreparationStatus.PREPARED;

  @Column(name = "project_id")
  private UUID projectId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "contract_version")
  private String contractVersion = "1.0";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "snapshot_json", columnDefinition = "jsonb")
  private Map<String, Object> snapshotJson;

  @Version
  @Column(name = "version")
  private Long version = 0L;

  protected ProjectGenerationPreparationEntity() {
  }

  public static ProjectGenerationPreparationEntity create(
      final UUID userId,
      final UUID chatId,
      final Long chatMessageCount,
      final String sourceChannel,
      final String projectName,
      final String ideTarget,
      final String promptSummary,
      final Map<String, Object> structuredAnalysis,
      final Map<String, Object> starterSelection,
      final String contractVersion,
      final Map<String, Object> snapshotJson) {
    final ProjectGenerationPreparationEntity entity = new ProjectGenerationPreparationEntity();
    entity.userId = userId;
    entity.chatId = chatId;
    entity.chatMessageCount = chatMessageCount;
    entity.sourceChannel = sourceChannel;
    entity.projectName = projectName;
    entity.ideTarget = ideTarget;
    entity.promptSummary = promptSummary;
    entity.structuredAnalysis = deepCopyMap(structuredAnalysis);
    entity.starterSelection = deepCopyMap(starterSelection);
    entity.status = ProjectGenerationPreparationStatus.PREPARED;
    entity.projectId = null;
    entity.contractVersion = contractVersion == null || contractVersion.isBlank() ? "1.0" : contractVersion.trim();
    entity.snapshotJson = deepCopyMap(snapshotJson);
    entity.version = 0L;
    return entity;
  }

  public String getContractVersion() {
    return contractVersion;
  }

  public Map<String, Object> getSnapshotJson() {
    return deepCopyMap(snapshotJson);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getChatId() {
    return chatId;
  }

  public Long getChatMessageCount() {
    return chatMessageCount;
  }

  public String getSourceChannel() {
    return sourceChannel;
  }

  public String getProjectName() {
    return projectName;
  }

  public String getIdeTarget() {
    return ideTarget;
  }

  public String getPromptSummary() {
    return promptSummary;
  }

  public ProjectGenerationPreparationStatus getStatus() {
    return status;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public void markAsStarted(final UUID startedProjectId) {
    if (status != ProjectGenerationPreparationStatus.PREPARED) {
      throw new IllegalStateException("Preparation is not in PREPARED state");
    }
    projectId = startedProjectId;
    status = ProjectGenerationPreparationStatus.STARTED;
  }

  public void markAsInvalidated() {
    if (status == ProjectGenerationPreparationStatus.STARTED) {
      throw new IllegalStateException("Started preparations cannot be invalidated");
    }
    status = ProjectGenerationPreparationStatus.INVALIDATED;
  }

  public Map<String, Object> snapshotStructuredAnalysis() {
    return deepCopyMap(structuredAnalysis);
  }

  public Map<String, Object> snapshotStarterSelection() {
    return deepCopyMap(starterSelection);
  }

  public Map<String, Object> snapshotPreparationSnapshot() {
    return deepCopyMap(snapshotJson);
  }

  private static Map<String, Object> deepCopyMap(final Map<String, Object> original) {
    if (original == null) {
      return Map.of();
    }
    final Map<String, Object> copy = new LinkedHashMap<>();
    for (final Map.Entry<String, Object> entry : original.entrySet()) {
      copy.put(entry.getKey(), deepCopyObject(entry.getValue()));
    }
    return copy;
  }

  private static Object deepCopyObject(final Object obj) {
    if (obj instanceof Map) {
      final Map<String, Object> copyMap = new LinkedHashMap<>();
      for (final Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
        if (entry.getKey() instanceof String) {
          copyMap.put((String) entry.getKey(), deepCopyObject(entry.getValue()));
        }
      }
      return copyMap;
    } else if (obj instanceof java.util.List) {
      final java.util.List<Object> copyList = new java.util.ArrayList<>();
      for (final Object item : (java.util.List<?>) obj) {
        copyList.add(deepCopyObject(item));
      }
      return copyList;
    } else {
      return obj;
    }
  }
}
