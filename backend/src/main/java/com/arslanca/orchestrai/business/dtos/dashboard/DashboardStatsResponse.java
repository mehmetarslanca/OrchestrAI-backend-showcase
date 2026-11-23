package com.arslanca.orchestrai.business.dtos.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

public record DashboardStatsResponse(
    int creditBalance,
    int totalCreditsUsed,
    int totalProjects,
    int activeOrchestrations,
    int completedProjects,
    int failedProjects,
    OffsetDateTime memberSince,
    BigDecimal last30DayEstimatedSpendUsd,
    BigDecimal last30DayReconciledSpendUsd
) {

    public DashboardStatsResponse {
        last30DayEstimatedSpendUsd = normalize(last30DayEstimatedSpendUsd);
        last30DayReconciledSpendUsd = normalize(last30DayReconciledSpendUsd);
    }

    private static BigDecimal normalize(final BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
