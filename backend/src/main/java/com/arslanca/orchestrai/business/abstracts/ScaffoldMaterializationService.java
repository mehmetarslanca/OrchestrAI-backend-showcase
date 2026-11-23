package com.arslanca.orchestrai.business.abstracts;

import java.util.UUID;

import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;

public interface ScaffoldMaterializationService {

    MaterializedStarter materialize(StarterSelection starterSelection, UUID projectId, Runnable cancellationProbe);
}
