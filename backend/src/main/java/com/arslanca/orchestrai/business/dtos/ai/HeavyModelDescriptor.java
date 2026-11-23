package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;

import java.util.List;
import java.util.UUID;

public record HeavyModelDescriptor(
        String modelKey,
        String provider,
        String family,
        String displayName,
        String source,
        boolean available,
        boolean defaultSelectable,
        boolean testOnly,
        boolean emulated,
        String gateway,
        String runtimeProvider,
        String runtimeTarget,
        List<String> badges,
        boolean selectable,
        CatalogModelAvailabilityState availabilityState,
        String connectionSurface,
        UUID selectedConnectionId,
        AiConnectionExecutionClass executionClass) {

    public HeavyModelDescriptor {
        badges = badges == null ? List.of() : List.copyOf(badges);
        selectable = selectable || (available && defaultSelectable);
        availabilityState = availabilityState == null
                ? (selectable ? CatalogModelAvailabilityState.SELECTABLE : CatalogModelAvailabilityState.UNSUPPORTED)
                : availabilityState;
    }

    public HeavyModelDescriptor(
            final String modelKey,
            final String provider,
            final String family,
            final String displayName,
            final String source,
            final boolean available) {
        this(modelKey, provider, family, displayName, source, available, true, false, false, null, null, null, List.of(), available, available ? CatalogModelAvailabilityState.SELECTABLE : CatalogModelAvailabilityState.UNSUPPORTED, null, null, null);
    }

    public HeavyModelDescriptor(
            final String modelKey,
            final String provider,
            final String family,
            final String displayName,
            final String source,
            final boolean available,
            final boolean defaultSelectable) {
        this(modelKey, provider, family, displayName, source, available, defaultSelectable, false, false, null, null, null, List.of(), available && defaultSelectable, available && defaultSelectable ? CatalogModelAvailabilityState.SELECTABLE : CatalogModelAvailabilityState.UNSUPPORTED, null, null, null);
    }

    public HeavyModelDescriptor(
            final String modelKey,
            final String provider,
            final String family,
            final String displayName,
            final String source,
            final boolean available,
            final boolean defaultSelectable,
            final boolean testOnly,
            final boolean emulated,
            final String gateway,
            final String runtimeProvider,
            final String runtimeTarget,
            final List<String> badges) {
        this(
                modelKey,
                provider,
                family,
                displayName,
                source,
                available,
                defaultSelectable,
                testOnly,
                emulated,
                gateway,
                runtimeProvider,
                runtimeTarget,
                badges,
                available && defaultSelectable,
                available && defaultSelectable ? CatalogModelAvailabilityState.SELECTABLE : CatalogModelAvailabilityState.UNSUPPORTED,
                null,
                null,
                null);
    }
}
