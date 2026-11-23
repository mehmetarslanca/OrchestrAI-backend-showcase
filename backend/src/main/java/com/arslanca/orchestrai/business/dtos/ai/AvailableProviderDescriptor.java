package com.arslanca.orchestrai.business.dtos.ai;

import java.util.List;

public record AvailableProviderDescriptor(
        String provider,
        String displayName,
        boolean testOnly,
        boolean emulated,
        List<String> badges) {

    public AvailableProviderDescriptor {
        badges = badges == null ? List.of() : List.copyOf(badges);
    }
}
