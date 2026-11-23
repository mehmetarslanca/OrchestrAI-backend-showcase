package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.AiUsageEventEntity;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiUsageEventRepository extends JpaRepository<AiUsageEventEntity, UUID> {

    @Query("""
            select sum(event.estimatedCostUsd)
            from AiUsageEventEntity event
            where event.userId = :userId
              and (:connectionId is null or event.connectionId = :connectionId)
              and event.startedAt >= :fromInclusive
              and event.startedAt < :toExclusive
            """)
    BigDecimal sumEstimatedCost(
            @Param("userId") UUID userId,
            @Param("connectionId") UUID connectionId,
            @Param("fromInclusive") OffsetDateTime fromInclusive,
            @Param("toExclusive") OffsetDateTime toExclusive);

    @Query("""
            select sum(event.reconciledCostUsd)
            from AiUsageEventEntity event
            where event.userId = :userId
              and (:connectionId is null or event.connectionId = :connectionId)
              and event.startedAt >= :fromInclusive
              and event.startedAt < :toExclusive
            """)
    BigDecimal sumReconciledCost(
            @Param("userId") UUID userId,
            @Param("connectionId") UUID connectionId,
            @Param("fromInclusive") OffsetDateTime fromInclusive,
            @Param("toExclusive") OffsetDateTime toExclusive);

    long countByUserIdAndConnectionIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID userId,
            UUID connectionId,
            OffsetDateTime fromInclusive,
            OffsetDateTime toExclusive);

    long countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID userId,
            OffsetDateTime fromInclusive,
            OffsetDateTime toExclusive);

    Optional<AiUsageEventEntity> findFirstByUserIdAndConnectionIdAndStatusOrderByCompletedAtDescCreatedAtDesc(
            UUID userId,
            UUID connectionId,
            AiUsageEventStatus status);

    Optional<AiUsageEventEntity> findFirstByUserIdAndStatusOrderByCompletedAtDescCreatedAtDesc(
            UUID userId,
            AiUsageEventStatus status);

    Optional<AiUsageEventEntity> findFirstByUserIdAndConnectionIdAndReconciliationStatusOrderByUpdatedAtDesc(
            UUID userId,
            UUID connectionId,
            AiUsageReconciliationStatus reconciliationStatus);
}
