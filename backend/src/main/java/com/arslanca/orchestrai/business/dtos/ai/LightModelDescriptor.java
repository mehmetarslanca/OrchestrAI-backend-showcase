package com.arslanca.orchestrai.business.dtos.ai;

public record LightModelDescriptor(
        String provider,
        String family,
        String displayName,
        boolean internalOnly) {
}
