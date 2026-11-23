package com.arslanca.orchestrai.dataaccess.abstracts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;

@Repository
public interface ProjectPlanningPreviewRepository extends JpaRepository<ProjectPlanningPreviewEntity, UUID> {

    List<ProjectPlanningPreviewEntity> findByPreparationIdOrderByCreatedAtDesc(UUID preparationId);

    Optional<ProjectPlanningPreviewEntity> findFirstByPreparationIdAndPreviewCacheKeyAndPreviewStatusInOrderByCreatedAtDesc(
            UUID preparationId,
            String previewCacheKey,
            Collection<PlanningPreviewStatus> previewStatuses);
}
