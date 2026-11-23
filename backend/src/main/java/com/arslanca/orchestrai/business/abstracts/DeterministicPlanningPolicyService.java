package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;

public interface DeterministicPlanningPolicyService {

    DeterministicPlanningProfile deriveProfile(StructuredAnalysis analysis);
}
