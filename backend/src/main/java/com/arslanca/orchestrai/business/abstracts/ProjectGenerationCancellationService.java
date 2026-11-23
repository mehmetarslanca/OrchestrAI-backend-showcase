package com.arslanca.orchestrai.business.abstracts;

import java.util.UUID;

import com.arslanca.orchestrai.business.concretes.cancellation.GenerationCancellationToken;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;

public interface ProjectGenerationCancellationService {

    GenerationCancellationToken openSession(UUID projectId);

    void cancelGeneration(UUID projectId, GenerationFailureReason reason);

    void closeSession(UUID projectId);
}
