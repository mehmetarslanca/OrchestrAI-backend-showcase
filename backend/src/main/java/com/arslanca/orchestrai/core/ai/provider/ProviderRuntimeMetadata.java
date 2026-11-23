package com.arslanca.orchestrai.core.ai.provider;

import java.util.List;

public record ProviderRuntimeMetadata(
        boolean testOnly,
        boolean emulated,
        String gateway,
        String runtimeProvider,
        String runtimeTarget,
        String credentialKind,
        List<String> badges) {

    public static ProviderRuntimeMetadata none() {
        return new ProviderRuntimeMetadata(false, false, null, null, null, null, List.of());
    }

    public ProviderRuntimeMetadata {
        badges = badges == null ? List.of() : List.copyOf(badges);
    }
}
