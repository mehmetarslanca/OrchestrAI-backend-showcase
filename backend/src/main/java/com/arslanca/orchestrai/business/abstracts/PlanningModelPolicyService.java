package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;

public interface PlanningModelPolicyService {

    PlanningModelPolicy resolvePolicy(
            PlanningComplexity complexity,
            ResolvedHeavyModel resolvedHeavyModel,
            boolean docsOnlyFallback);
}
