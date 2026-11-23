package com.arslanca.orchestrai.business.abstracts;

import java.util.function.BiConsumer;

import com.arslanca.orchestrai.business.concretes.cancellation.GenerationCancellationToken;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStage;
import com.arslanca.orchestrai.business.dtos.projectgeneration.OrchestrationResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;

public interface OrchestrationService {

    OrchestrationResult orchestrate(
            String userPrompt,
            String ideTarget,
            ResolvedHeavyModel resolvedHeavyModel,
            GenerationCancellationToken cancellationToken,
            BiConsumer<GenerationStage, String> progressCallback);

    OrchestrationResult orchestratePrepared(
            StructuredAnalysis analysis,
            String ideTarget,
            ResolvedHeavyModel resolvedHeavyModel,
            StarterSelection starterSelection,
            GenerationCancellationToken cancellationToken,
            BiConsumer<GenerationStage, String> progressCallback);

    default OrchestrationResult orchestratePrepared(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final GenerationCancellationToken cancellationToken,
            final BiConsumer<GenerationStage, String> progressCallback,
            final GenerationUsageContext generationUsageContext) {
        return orchestratePrepared(
                analysis,
                ideTarget,
                resolvedHeavyModel,
                starterSelection,
                cancellationToken,
                progressCallback);
    }
}
