package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT p FROM ProjectEntity p WHERE p.id = :id")
    Optional<ProjectEntity> findByIdForUpdate(@Param("id") UUID id);

    Page<ProjectEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndStatus(UUID userId, ProjectStatus status);

    long countByUserId(UUID userId);

    void deleteAllByUserId(UUID userId);

    long countByStatus(ProjectStatus status);

    java.util.List<ProjectEntity> findByStatus(ProjectStatus status);

    java.util.List<ProjectEntity> findByStatusAndUpdatedAtBefore(ProjectStatus status, java.time.OffsetDateTime threshold);
}
