package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StarterCatalogStatus {

    ACTIVE,
    DISABLED;

    @JsonCreator
    public static StarterCatalogStatus fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> ACTIVE;
            case "disabled" -> DISABLED;
            default -> throw new IllegalArgumentException("Unsupported starter catalog status: " + value);
        };
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean selectable() {
        return this == ACTIVE;
    }
}
