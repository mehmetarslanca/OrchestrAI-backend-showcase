package com.arslanca.orchestrai.business.planning.governance;

import com.arslanca.orchestrai.business.dtos.orchestration.PolicyVersionFingerprint;
import org.springframework.stereotype.Component;

@Component
public class DefaultPolicyVersionFingerprintResolver implements PolicyVersionFingerprintResolver {

    private final PlanningBudgetPolicy planningBudgetPolicy;
    private final PreviewBillingPolicy previewBillingPolicy;
    private final PlanningRolloutPolicy planningRolloutPolicy;

    public DefaultPolicyVersionFingerprintResolver(
            final PlanningBudgetPolicy planningBudgetPolicy,
            final PreviewBillingPolicy previewBillingPolicy,
            final PlanningRolloutPolicy planningRolloutPolicy) {
        this.planningBudgetPolicy = planningBudgetPolicy;
        this.previewBillingPolicy = previewBillingPolicy;
        this.planningRolloutPolicy = planningRolloutPolicy;
    }

    @Override
    public PolicyVersionFingerprint resolve(final PlanningGovernanceContext context) {
        final String planningFingerprint = context != null
                && context.planningModelPolicy() != null
                && context.planningModelPolicy().policyVersionFingerprint() != null
                        ? context.planningModelPolicy().policyVersionFingerprint().value()
                        : PolicyVersionFingerprint.unknown().value();
        return new PolicyVersionFingerprint(String.join(
                ":",
                planningFingerprint,
                planningBudgetPolicy.policyVersion(),
                previewBillingPolicy.policyVersion(),
                DefaultPreviewCachePolicy.POLICY_VERSION,
                planningRolloutPolicy.policyVersion()));
    }
}
