package com.arslanca.orchestrai.business.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AiUsageSpendSummary(
        UUID connectionId,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        BigDecimal estimatedCostUsd,
        BigDecimal reconciledCostUsd,
        long eventCount,
        OffsetDateTime lastRecordedAt) {

    public AiUsageSpendSummary {
        estimatedCostUsd = normalize(estimatedCostUsd);
        reconciledCostUsd = normalize(reconciledCostUsd);
    }

    private static BigDecimal normalize(final BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
