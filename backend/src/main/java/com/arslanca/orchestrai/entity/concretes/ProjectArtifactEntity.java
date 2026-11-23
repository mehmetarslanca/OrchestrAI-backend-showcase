package com.arslanca.orchestrai.entity.concretes;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactData;
import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactProvenance;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_artifacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProjectArtifactEntity {

    @Id
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_tree", nullable = false, columnDefinition = "jsonb")
    private ProjectArtifactData finalTree;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private ProjectArtifactProvenance metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
