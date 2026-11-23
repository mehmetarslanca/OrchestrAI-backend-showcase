package com.arslanca.orchestrai.business.dtos.project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectArtifactResponse(
        UUID projectId,
        ProjectArtifactData finalTree,
        ProjectArtifactProvenance metadata,
        OffsetDateTime createdAt) {
}
