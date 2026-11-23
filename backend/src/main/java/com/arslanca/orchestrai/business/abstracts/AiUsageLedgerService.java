package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.usage.AiConnectionHealthSummary;
import com.arslanca.orchestrai.business.usage.AiUsageEventCommand;
import com.arslanca.orchestrai.business.usage.AiUsageSpendSummary;
import com.arslanca.orchestrai.entity.enums.AiUsageReconciliationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AiUsageLedgerService {
    UUID recordEvent(AiUsageEventCommand command);
    void reconcileEvent(UUID eventId, BigDecimal reconciledCostUsd, AiUsageReconciliationStatus reconciliationStatus);
    AiUsageSpendSummary summarizeSpend(UUID userId, OffsetDateTime fromInclusive, OffsetDateTime toExclusive);
    AiUsageSpendSummary summarizeConnectionSpend(UUID userId, UUID connectionId, OffsetDateTime fromInclusive, OffsetDateTime toExclusive);
    Optional<AiConnectionHealthSummary> getConnectionHealthSummary(UUID userId, UUID connectionId);
}
