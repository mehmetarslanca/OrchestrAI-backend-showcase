package com.arslanca.orchestrai.business.usage;

import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record AiPricingSnapshotCommand(
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
        Map<String, Object> metadata) {

    public AiPricingSnapshotCommand {
        providerKey = requireText(providerKey, "providerKey");
        connectionType = require(connectionType, "connectionType");
        billingMode = require(billingMode, "billingMode");
        modelKey = requireText(modelKey, "modelKey");
        concreteModelId = normalize(concreteModelId);
        rateCardVersion = normalize(rateCardVersion);
        validateNonNegative(inputCostPerMillionUsd, "inputCostPerMillionUsd");
        validateNonNegative(outputCostPerMillionUsd, "outputCostPerMillionUsd");
        validateNonNegative(cachedInputCostPerMillionUsd, "cachedInputCostPerMillionUsd");
        validateNonNegative(reasoningCostPerMillionUsd, "reasoningCostPerMillionUsd");
        metadata = copyMetadata(metadata);
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

    private static void validateNonNegative(final BigDecimal value, final String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative.");
        }
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
