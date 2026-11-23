package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.usage.AiPricingSnapshot;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshotCommand;
import com.arslanca.orchestrai.business.usage.AiTokenUsage;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface AiPricingSnapshotService {
    AiPricingSnapshot captureSnapshot(AiPricingSnapshotCommand command);
    Optional<AiPricingSnapshot> findSnapshot(UUID snapshotId);
    AiPricingSnapshot getSnapshot(UUID snapshotId);
    Optional<BigDecimal> estimateCost(AiPricingSnapshot snapshot, AiTokenUsage tokenUsage);
}
