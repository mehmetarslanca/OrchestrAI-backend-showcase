package com.arslanca.orchestrai.business.usage;

import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AiPricingSnapshot(
        UUID id,
        String providerKey,
        AiConnectionType connectionType,
        AiConnectionBillingMode billingMode,
        String modelKey,
        String concreteModelId,
        String rateCardVersion,
        BigDecimal inputCostPerMillionUsd,
        BigDecimal outputCostPerMillionUsd,
        BigDecimal cachedInputCostPerMillionUsd,
        BigDecimal reasoningCostPerMillionUsd,
        Map<String, Object> metadata,
        OffsetDateTime capturedAt) {

    public AiPricingSnapshot {
        id = require(id, "id");
        providerKey = requireText(providerKey, "providerKey");
        connectionType = require(connectionType, "connectionType");
        billingMode = require(billingMode, "billingMode");
        modelKey = requireText(modelKey, "modelKey");
        concreteModelId = normalize(concreteModelId);
        rateCardVersion = normalize(rateCardVersion);
        inputCostPerMillionUsd = normalizeDecimal(inputCostPerMillionUsd);
        outputCostPerMillionUsd = normalizeDecimal(outputCostPerMillionUsd);
        cachedInputCostPerMillionUsd = normalizeDecimal(cachedInputCostPerMillionUsd);
        reasoningCostPerMillionUsd = normalizeDecimal(reasoningCostPerMillionUsd);
        metadata = copyMetadata(metadata);
        capturedAt = require(capturedAt, "capturedAt");
    }

    public boolean hasAnyTokenPricing() {
        return inputCostPerMillionUsd != null
                || outputCostPerMillionUsd != null
                || cachedInputCostPerMillionUsd != null
                || reasoningCostPerMillionUsd != null;
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

    private static BigDecimal normalizeDecimal(final BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Pricing values cannot be negative.");
        }
        return value.setScale(8, RoundingMode.HALF_UP);
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
