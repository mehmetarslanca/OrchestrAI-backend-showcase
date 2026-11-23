package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiPricingSnapshotService;
import com.arslanca.orchestrai.business.abstracts.AiUsageLedgerService;
import com.arslanca.orchestrai.business.usage.AiConnectionHealthSummary;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshot;
import com.arslanca.orchestrai.business.usage.AiUsageEventCommand;
import com.arslanca.orchestrai.business.usage.AiUsageSpendSummary;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.AiUsageEventRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.entity.concretes.AiUsageEventEntity;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageReconciliationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiUsageLedgerManager implements AiUsageLedgerService {

    private final AiUsageEventRepository aiUsageEventRepository;
    private final UserAiConnectionRepository userAiConnectionRepository;
    private final AiPricingSnapshotService aiPricingSnapshotService;

    @Override
    @Transactional
    public UUID recordEvent(final AiUsageEventCommand command) {
        final BigDecimal estimatedCostUsd = resolveEstimatedCost(command).orElse(null);
        final AiUsageEventEntity entity = AiUsageEventEntity.create(command, estimatedCostUsd);
        return aiUsageEventRepository.save(entity).getId();
    }

    @Override
    @Transactional
    public void reconcileEvent(
            final UUID eventId,
            final BigDecimal reconciledCostUsd,
            final AiUsageReconciliationStatus reconciliationStatus) {
        final AiUsageEventEntity entity = aiUsageEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("AI usage event not found: " + eventId));
        entity.reconcile(reconciledCostUsd, reconciliationStatus);
        aiUsageEventRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public AiUsageSpendSummary summarizeSpend(
            final UUID userId,
            final OffsetDateTime fromInclusive,
            final OffsetDateTime toExclusive) {
        return buildSpendSummary(userId, null, fromInclusive, toExclusive);
    }

    @Override
    @Transactional(readOnly = true)
    public AiUsageSpendSummary summarizeConnectionSpend(
            final UUID userId,
            final UUID connectionId,
            final OffsetDateTime fromInclusive,
            final OffsetDateTime toExclusive) {
        return buildSpendSummary(userId, connectionId, fromInclusive, toExclusive);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiConnectionHealthSummary> getConnectionHealthSummary(final UUID userId, final UUID connectionId) {
        return userAiConnectionRepository.findByIdAndUserId(connectionId, userId)
                .map(connection -> new AiConnectionHealthSummary(
                        connection.getId(),
                        connection.getProviderKey(),
                        connection.getConnectionStatus(),
                        connection.getLastValidatedAt(),
                        requiresReauth(connection),
                        aiUsageEventRepository
                                .findFirstByUserIdAndConnectionIdAndStatusOrderByCompletedAtDescCreatedAtDesc(
                                        userId,
                                        connectionId,
                                        AiUsageEventStatus.COMPLETED)
                                .map(AiUsageEventEntity::getCompletedAt)
                                .orElse(null),
                        aiUsageEventRepository
                                .findFirstByUserIdAndConnectionIdAndReconciliationStatusOrderByUpdatedAtDesc(
                                        userId,
                                        connectionId,
                                        AiUsageReconciliationStatus.RECONCILED)
                                .map(AiUsageEventEntity::getUpdatedAt)
                                .orElse(null)));
    }

    private Optional<BigDecimal> resolveEstimatedCost(final AiUsageEventCommand command) {
        if (command.pricingSnapshotId() == null) {
            return Optional.empty();
        }
        final AiPricingSnapshot snapshot = aiPricingSnapshotService.getSnapshot(command.pricingSnapshotId());
        validateSnapshotCompatibility(command, snapshot);
        return aiPricingSnapshotService.estimateCost(snapshot, command.tokenUsage());
    }

    private void validateSnapshotCompatibility(final AiUsageEventCommand command, final AiPricingSnapshot snapshot) {
        if (!snapshot.providerKey().equals(command.providerKey())) {
            throw new IllegalArgumentException("pricing snapshot providerKey does not match usage event providerKey.");
        }
        if (snapshot.connectionType() != command.connectionType()) {
            throw new IllegalArgumentException("pricing snapshot connectionType does not match usage event connectionType.");
        }
        if (snapshot.billingMode() != command.billingMode()) {
            throw new IllegalArgumentException("pricing snapshot billingMode does not match usage event billingMode.");
        }
        if (!snapshot.modelKey().equals(command.modelKey())) {
            throw new IllegalArgumentException("pricing snapshot modelKey does not match usage event modelKey.");
        }
        if (snapshot.concreteModelId() != null
                && command.concreteModelId() != null
                && !snapshot.concreteModelId().equals(command.concreteModelId())) {
            throw new IllegalArgumentException("pricing snapshot concreteModelId does not match usage event concreteModelId.");
        }
    }

    private AiUsageSpendSummary buildSpendSummary(
            final UUID userId,
            final UUID connectionId,
            final OffsetDateTime fromInclusive,
            final OffsetDateTime toExclusive) {
        validateWindow(fromInclusive, toExclusive);
        final BigDecimal estimatedCostUsd = aiUsageEventRepository
                .sumEstimatedCost(userId, connectionId, fromInclusive, toExclusive);
        final BigDecimal reconciledCostUsd = aiUsageEventRepository
                .sumReconciledCost(userId, connectionId, fromInclusive, toExclusive);
        final long eventCount = connectionId == null
                ? aiUsageEventRepository.countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        userId,
                        fromInclusive,
                        toExclusive)
                : aiUsageEventRepository.countByUserIdAndConnectionIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        userId,
                        connectionId,
                        fromInclusive,
                        toExclusive);
        final OffsetDateTime lastRecordedAt = connectionId == null
                ? aiUsageEventRepository
                        .findFirstByUserIdAndStatusOrderByCompletedAtDescCreatedAtDesc(
                                userId,
                                AiUsageEventStatus.COMPLETED)
                        .map(AiUsageEventEntity::getCompletedAt)
                        .orElse(null)
                : aiUsageEventRepository
                        .findFirstByUserIdAndConnectionIdAndStatusOrderByCompletedAtDescCreatedAtDesc(
                                userId,
                                connectionId,
                                AiUsageEventStatus.COMPLETED)
                        .map(AiUsageEventEntity::getCompletedAt)
                        .orElse(null);
        return new AiUsageSpendSummary(
                connectionId,
                fromInclusive,
                toExclusive,
                estimatedCostUsd,
                reconciledCostUsd,
                eventCount,
                lastRecordedAt);
    }

    private void validateWindow(final OffsetDateTime fromInclusive, final OffsetDateTime toExclusive) {
        if (fromInclusive == null || toExclusive == null) {
            throw new IllegalArgumentException("fromInclusive and toExclusive must be provided.");
        }
        if (!toExclusive.isAfter(fromInclusive)) {
            throw new IllegalArgumentException("toExclusive must be after fromInclusive.");
        }
    }

    private boolean requiresReauth(final UserAiConnectionEntity connection) {
        final AiConnectionStatus status = connection.getConnectionStatus();
        return connection.getReauthRequiredAt() != null
                || status == AiConnectionStatus.REAUTH_REQUIRED
                || status == AiConnectionStatus.EXPIRED;
    }
}
