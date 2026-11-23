package com.arslanca.orchestrai.business.runtime;

import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;

import java.util.UUID;

public record AiUsageRuntimeDescriptor(
        UUID connectionId,
        String providerKey,
        AiConnectionType connectionType,
        AiConnectionBillingMode billingMode,
        String modelKey,
        String concreteModelId) {

    public AiUsageRuntimeDescriptor {
        providerKey = requireText(providerKey, "providerKey");
        connectionType = require(connectionType, "connectionType");
        billingMode = require(billingMode, "billingMode");
        modelKey = requireText(modelKey, "modelKey");
        concreteModelId = normalize(concreteModelId);
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
}
