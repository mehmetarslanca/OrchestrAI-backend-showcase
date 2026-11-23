package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiPricingSnapshotService;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshot;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshotCommand;
import com.arslanca.orchestrai.business.usage.AiTokenUsage;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.AiPricingSnapshotRepository;
import com.arslanca.orchestrai.entity.concretes.AiPricingSnapshotEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiPricingSnapshotManager implements AiPricingSnapshotService {

    private static final BigDecimal TOKENS_PER_MILLION = BigDecimal.valueOf(1_000_000L);

    private final AiPricingSnapshotRepository aiPricingSnapshotRepository;

    @Override
    @Transactional
    public AiPricingSnapshot captureSnapshot(final AiPricingSnapshotCommand command) {
        final AiPricingSnapshotEntity entity = AiPricingSnapshotEntity.create(command);
        return toSnapshot(aiPricingSnapshotRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiPricingSnapshot> findSnapshot(final UUID snapshotId) {
        return aiPricingSnapshotRepository.findById(snapshotId).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public AiPricingSnapshot getSnapshot(final UUID snapshotId) {
        return findSnapshot(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("AI pricing snapshot not found: " + snapshotId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> estimateCost(final AiPricingSnapshot snapshot, final AiTokenUsage tokenUsage) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must be provided.");
        }
        if (tokenUsage == null) {
            throw new IllegalArgumentException("tokenUsage must be provided.");
        }
        if (!snapshot.hasAnyTokenPricing()) {
            return Optional.empty();
        }
        if (hasUnpricedUsage(tokenUsage.inputTokens(), snapshot.inputCostPerMillionUsd())
                || hasUnpricedUsage(tokenUsage.outputTokens(), snapshot.outputCostPerMillionUsd())
                || hasUnpricedUsage(tokenUsage.cachedInputTokens(), snapshot.cachedInputCostPerMillionUsd())
                || hasUnpricedUsage(tokenUsage.reasoningTokens(), snapshot.reasoningCostPerMillionUsd())) {
            return Optional.empty();
        }

        BigDecimal total = BigDecimal.ZERO;
        total = total.add(componentCost(tokenUsage.inputTokens(), snapshot.inputCostPerMillionUsd()));
        total = total.add(componentCost(tokenUsage.outputTokens(), snapshot.outputCostPerMillionUsd()));
        total = total.add(componentCost(tokenUsage.cachedInputTokens(), snapshot.cachedInputCostPerMillionUsd()));
        total = total.add(componentCost(tokenUsage.reasoningTokens(), snapshot.reasoningCostPerMillionUsd()));
        return Optional.of(total.setScale(8, RoundingMode.HALF_UP));
    }

    private BigDecimal componentCost(final long tokens, final BigDecimal ratePerMillionUsd) {
        if (tokens <= 0 || ratePerMillionUsd == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens)
                .multiply(ratePerMillionUsd)
                .divide(TOKENS_PER_MILLION, 8, RoundingMode.HALF_UP);
    }

    private boolean hasUnpricedUsage(final long tokens, final BigDecimal ratePerMillionUsd) {
        return tokens > 0 && ratePerMillionUsd == null;
    }

    private AiPricingSnapshot toSnapshot(final AiPricingSnapshotEntity entity) {
        return new AiPricingSnapshot(
                entity.getId(),
                entity.getProviderKey(),
                entity.getConnectionType(),
                entity.getBillingMode(),
                entity.getModelKey(),
                entity.getConcreteModelId(),
                entity.getRateCardVersion(),
                entity.getInputCostPerMillionUsd(),
                entity.getOutputCostPerMillionUsd(),
                entity.getCachedInputCostPerMillionUsd(),
                entity.getReasoningCostPerMillionUsd(),
                entity.getMetadata(),
                entity.getCapturedAt());
    }
}
