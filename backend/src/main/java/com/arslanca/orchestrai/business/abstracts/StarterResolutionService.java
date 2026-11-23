package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;

public interface StarterResolutionService {

    StarterSelection resolveStarter(StructuredAnalysis analysis, String ideTarget);
}
