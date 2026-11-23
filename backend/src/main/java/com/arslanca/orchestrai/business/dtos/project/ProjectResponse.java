package com.arslanca.orchestrai.business.dtos.project;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String projectName,
        String ideTarget,
        String status,
        String originalPrompt,
        Map<String, Object> metadata,
        OffsetDateTime createdAt) {
}
