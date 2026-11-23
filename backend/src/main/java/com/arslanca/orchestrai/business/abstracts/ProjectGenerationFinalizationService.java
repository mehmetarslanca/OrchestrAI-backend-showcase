package com.arslanca.orchestrai.business.abstracts;

import java.util.UUID;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;

public interface ProjectGenerationFinalizationService {

    void finalizeSuccess(
            UUID projectId,
            UUID userId,
            String creditReservationReference,
            DeliveryResult result,
            ResolvedHeavyModel resolvedHeavyModel,
            com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection starterSelection,
            com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter materializedStarter,
            PlanningQualitySummary planningQualitySummary,
            PlanningRepairOutcome planningRepairOutcome);
}
