package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;

public record PlannerModelRef(
        String resolvedModelKey,
        ModelCapability modelCapability,
        boolean localModel) {

    public PlannerModelRef {
        resolvedModelKey = resolvedModelKey == null ? "" : resolvedModelKey.trim();
        modelCapability = modelCapability == null ? ModelCapability.UNKNOWN : modelCapability;
    }

    public static PlannerModelRef fromResolvedHeavyModel(final ResolvedHeavyModel resolvedHeavyModel) {
        if (resolvedHeavyModel == null) {
            return unknown();
        }
        return new PlannerModelRef(
                resolvedHeavyModel.modelKey(),
                resolvedHeavyModel.capability(),
                resolvedHeavyModel.isLocalModel());
    }

    public static PlannerModelRef unknown() {
        return new PlannerModelRef("", ModelCapability.UNKNOWN, false);
    }

    public String stableValue() {
        final String normalizedModelKey = resolvedModelKey.isBlank() ? "unknown" : resolvedModelKey;
        return normalizedModelKey + "|" + modelCapability.name().toLowerCase(Locale.ROOT) + "|" + (localModel ? "local" : "remote");
    }
}
