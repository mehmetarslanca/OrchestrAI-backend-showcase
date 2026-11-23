package com.arslanca.orchestrai.dataaccess.abstracts;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface ProjectGenerationPreparationRepository extends JpaRepository<ProjectGenerationPreparationEntity, UUID> {

    Optional<ProjectGenerationPreparationEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<ProjectGenerationPreparationEntity> findFirstByUserIdAndChatIdAndChatMessageCountAndIdeTargetAndStatusOrderByCreatedAtDesc(
            UUID userId,
            UUID chatId,
            Long chatMessageCount,
            String ideTarget,
            ProjectGenerationPreparationStatus status);

    Optional<ProjectGenerationPreparationEntity> findFirstByUserIdAndChatIdAndChatMessageCountAndStatusOrderByCreatedAtDesc(
            UUID userId,
            UUID chatId,
            Long chatMessageCount,
            ProjectGenerationPreparationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT preparation FROM ProjectGenerationPreparationEntity preparation WHERE preparation.id = :id AND preparation.userId = :userId")
    Optional<ProjectGenerationPreparationEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
