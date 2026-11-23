package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum QualityVerdict {
    ACCEPT("accept"),
    TARGETED_EXPANSION("targeted_expansion"),
    FULL_REGENERATE("full_regenerate"),
    REJECT("reject");

    private final String value;

    QualityVerdict(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static QualityVerdict fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return REJECT;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final QualityVerdict verdict : values()) {
            if (verdict.value.equals(normalizedValue)) {
                return verdict;
            }
        }
        return REJECT;
    }
}
