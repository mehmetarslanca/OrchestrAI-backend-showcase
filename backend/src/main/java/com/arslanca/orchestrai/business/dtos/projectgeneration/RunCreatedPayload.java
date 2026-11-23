package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.UUID;

public record RunCreatedPayload(
        UUID projectId,
        UUID preparationId,
        String projectName,
        String status,
        String ideTarget
) {
}
