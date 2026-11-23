package com.arslanca.orchestrai.business.generation.runtime;

import java.util.UUID;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PreparedGenerationLaunch;
import com.arslanca.orchestrai.business.dtos.projectgeneration.RunCreatedPayload;
import com.arslanca.orchestrai.entity.enums.IdeTarget;

public record PreparedGenerationStartContext(
        UUID projectId,
        String selectedStarterKey,
        IdeTarget ideTarget,
        String creditReservationReference,
        ResolvedHeavyModel resolvedHeavyModel,
        PreparedGenerationLaunch launch,
        RunCreatedPayload runCreatedPayload) {
}
