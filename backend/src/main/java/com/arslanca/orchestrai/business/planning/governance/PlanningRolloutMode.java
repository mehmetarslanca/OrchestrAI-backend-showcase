package com.arslanca.orchestrai.business.planning.governance;

import java.util.Locale;

public enum PlanningRolloutMode {
    OFF("off"),
    SHADOW("shadow"),
    ENFORCED("enforced");

    private final String value;

    PlanningRolloutMode(final String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static PlanningRolloutMode fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final PlanningRolloutMode mode : values()) {
            if (mode.value.equals(normalizedValue)) {
                return mode;
            }
        }
        return OFF;
    }
}
