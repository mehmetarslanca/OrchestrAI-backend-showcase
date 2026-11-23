package com.arslanca.orchestrai.business.dtos.projectgeneration;

public enum GenerationSourceChannel {
    WEB,
    CHAT;

    public static GenerationSourceChannel fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return WEB;
        }

        return GenerationSourceChannel.valueOf(value.trim().toUpperCase());
    }
}
