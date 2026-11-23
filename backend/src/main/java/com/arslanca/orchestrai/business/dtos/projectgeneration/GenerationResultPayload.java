package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.UUID;

public record GenerationResultPayload(
        UUID projectId,
        String projectName,
        String status,
        boolean artifactAvailable,
        String ideTarget) {
}
