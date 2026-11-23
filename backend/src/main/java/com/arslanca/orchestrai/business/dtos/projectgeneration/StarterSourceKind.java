package com.arslanca.orchestrai.business.dtos.projectgeneration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StarterSourceKind {
    REPO_PACK("repo-pack"),
    OFFICIAL_ADAPTER("official-adapter");

    private final String value;

    StarterSourceKind(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static StarterSourceKind fromValue(final String value) {
        for (final StarterSourceKind sourceKind : values()) {
            if (sourceKind.value.equalsIgnoreCase(value)) {
                return sourceKind;
            }
        }
        throw new IllegalArgumentException("Unsupported starter source kind: " + value);
    }
}
