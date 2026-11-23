package com.arslanca.orchestrai.business.abstracts;

import java.util.UUID;

import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;

public interface PreparedProjectGenerationService {

    UUID startGeneration(UUID userId, GenerationStartRequest request, SseEventPublisher publisher);

    UUID startGenerationWithOverride(
            UUID userId,
            GenerationStartRequest request,
            String heavyModelKeyOverride,
            CredentialSource heavyModelAuthSourceOverride,
            SseEventPublisher publisher);

    void cancelGeneration(UUID userId, UUID projectId);
}
