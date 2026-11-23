package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.entity.enums.IdeTarget;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "ide_target", nullable = false, length = 30)
    private IdeTarget ideTarget;

    @Column(name = "original_prompt", nullable = false, columnDefinition = "TEXT")
    private String originalPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.PENDING;

    /**
     * Ek metadata: token kullanımı, LLM model bilgisi, süre vb.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    // ---- Domain Methods ----

    /**
     * Proje durumunu PROCESSING'e geçirir. Sadece PENDING'den geçiş yapılabilir.
     */
    public void markAsProcessing() {
        validateStatusTransition(ProjectStatus.PENDING, ProjectStatus.PROCESSING);
        this.status = ProjectStatus.PROCESSING;
    }

    /**
     * Proje durumunu COMPLETED'e geçirir ve metadata kaydeder.
     */
    public void markAsCompleted(final Map<String, Object> generationMetadata) {
        validateStatusTransition(ProjectStatus.PROCESSING, ProjectStatus.COMPLETED);
        this.status = ProjectStatus.COMPLETED;
        this.metadata = mergeMetadata(generationMetadata);
    }

    /**
     * Proje durumunu FAILED'e geçirir.
     */
    public void markAsFailed(final Map<String, Object> failureMetadata) {
        if (this.status != ProjectStatus.PENDING && this.status != ProjectStatus.PROCESSING) {
            throw new IllegalStateException(
                    String.format("Geçersiz durum geçişi: %s → %s. Beklenen mevcut durum: %s veya %s",
                            this.status, ProjectStatus.FAILED, ProjectStatus.PENDING, ProjectStatus.PROCESSING));
        }
        this.status = ProjectStatus.FAILED;
        this.metadata = mergeMetadata(failureMetadata);
    }

    public boolean isActive() {
        return this.status == ProjectStatus.PENDING || this.status == ProjectStatus.PROCESSING;
    }

    public boolean isTerminal() {
        return this.status == ProjectStatus.COMPLETED || this.status == ProjectStatus.FAILED;
    }

    public void appendMetadata(final Map<String, Object> metadataUpdate) {
        this.metadata = mergeMetadata(metadataUpdate);
    }

    private void validateStatusTransition(final ProjectStatus expectedCurrent, final ProjectStatus targetStatus) {
        if (this.status != expectedCurrent) {
            throw new IllegalStateException(
                    String.format("Geçersiz durum geçişi: %s → %s. Beklenen mevcut durum: %s",
                            this.status, targetStatus, expectedCurrent));
        }
    }

    private Map<String, Object> mergeMetadata(final Map<String, Object> metadataUpdate) {
        final Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (this.metadata != null) {
            mergedMetadata.putAll(this.metadata);
        }
        if (metadataUpdate != null) {
            mergedMetadata.putAll(metadataUpdate);
        }
        return mergedMetadata;
    }
}
