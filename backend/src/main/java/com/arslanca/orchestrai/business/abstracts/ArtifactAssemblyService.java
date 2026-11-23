package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.entity.enums.IdeTarget;

public interface ArtifactAssemblyService {

    /**
     * Assembles the BlueprintPlan and MaterializedStarter into a final IDE-specific DeliveryResult.
     */
    DeliveryResult assembleArtifact(BlueprintPlan blueprint, MaterializedStarter starter, IdeTarget ideTarget);
}
