package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RepairAction {
    TARGETED_EXPANSION("targeted_expansion"),
    AGENT_REGENERATION("agent_regeneration"),
    RULES_REPAIR("rules_repair"),
    FULL_REGENERATE("full_regenerate"),
    UNKNOWN("unknown");

    private final String value;

    RepairAction(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static RepairAction fromValue(final String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedText = text.trim().toLowerCase(Locale.ROOT);
        for (final RepairAction action : values()) {
            if (action.value.equals(normalizedText)) {
                return action;
            }
        }
        return UNKNOWN;
    }
}
