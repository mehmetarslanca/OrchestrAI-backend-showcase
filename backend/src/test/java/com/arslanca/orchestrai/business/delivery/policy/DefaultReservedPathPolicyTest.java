package com.arslanca.orchestrai.business.delivery.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultReservedPathPolicyTest {

    @Test
    void shouldIdentifyReservedPaths() {
        DefaultReservedPathPolicy policy = new DefaultReservedPathPolicy();

        assertThat(policy.isReserved(".cursorrules")).isTrue();
        assertThat(policy.isReserved("docs/architect.md")).isTrue();
        assertThat(policy.isReserved(".agents/rules/rules.md")).isTrue();
        assertThat(policy.isReserved("docs/phases/phase_1.md")).isTrue();
        assertThat(policy.isReserved("docs/agents/backend-engineer.md")).isTrue();
        assertThat(policy.isReserved("DOCS/AGENTS/backend-engineer.md")).isTrue();

        assertThat(policy.isReserved("src/main/java/Main.java")).isFalse();
        assertThat(policy.isReserved("README.md")).isFalse();
    }
}
