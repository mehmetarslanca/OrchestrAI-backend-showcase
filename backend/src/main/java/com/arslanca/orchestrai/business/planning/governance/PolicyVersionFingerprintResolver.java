package com.arslanca.orchestrai.business.planning.governance;

import com.arslanca.orchestrai.business.dtos.orchestration.PolicyVersionFingerprint;

public interface PolicyVersionFingerprintResolver {
    PolicyVersionFingerprint resolve(PlanningGovernanceContext context);
}
