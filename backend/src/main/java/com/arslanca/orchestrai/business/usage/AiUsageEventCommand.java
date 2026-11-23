package com.arslanca.orchestrai.business.usage;

import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageReconciliationStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSource;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AiUsageEventCommand(
        UUID userId,
        UUID connectionId,
        UUID pricingSnapshotId,
        String requestId,
        String providerKey,
        AiConnectionType connectionType,
        AiConnectionBillingMode billingMode,
        AiUsageSurface surface,
        String modelKey,
        String concreteModelId,
        String providerRequestId,
        AiUsageSource usageSource,
        AiTokenUsage tokenUsage,
        AiUsageReconciliationStatus reconciliationStatus,
        AiUsageEventStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Map<String, Object> metadata) {

    public AiUsageEventCommand {
        userId = require(userId, "userId");
        requestId = normalize(requestId);
        providerKey = requireText(providerKey, "providerKey");
        connectionType = require(connectionType, "connectionType");
        billingMode = require(billingMode, "billingMode");
        surface = require(surface, "surface");
        modelKey = requireText(modelKey, "modelKey");
        concreteModelId = normalize(concreteModelId);
        providerRequestId = normalize(providerRequestId);
        usageSource = require(usageSource, "usageSource");
        tokenUsage = require(tokenUsage, "tokenUsage");
        reconciliationStatus = reconciliationStatus == null
                ? AiUsageReconciliationStatus.PENDING
                : reconciliationStatus;
        status = require(status, "status");
        startedAt = require(startedAt, "startedAt");
        completedAt = normalizeCompletedAt(status, startedAt, completedAt);
        metadata = copyMetadata(metadata);
    }

    private static OffsetDateTime normalizeCompletedAt(
            final AiUsageEventStatus status,
            final OffsetDateTime startedAt,
            final OffsetDateTime completedAt) {
        if (status == AiUsageEventStatus.STARTED) {
            if (completedAt != null) {
                throw new IllegalArgumentException("STARTED events cannot have completedAt.");
            }
            return null;
        }
        final OffsetDateTime effectiveCompletedAt = completedAt == null ? startedAt : completedAt;
        if (effectiveCompletedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt cannot be before startedAt.");
        }
        return effectiveCompletedAt;
    }

    private static String requireText(final String value, final String field) {
        final String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must be provided.");
        }
        return normalized;
    }

    private static <T> T require(final T value, final String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must be provided.");
        }
        return value;
    }

    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, Object> copyMetadata(final Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(metadata));
    }
}
