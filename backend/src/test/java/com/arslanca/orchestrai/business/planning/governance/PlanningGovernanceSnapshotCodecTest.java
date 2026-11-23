package com.arslanca.orchestrai.business.planning.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class PlanningGovernanceSnapshotCodecTest {

    private PlanningGovernanceSnapshotCodec codec;

    @BeforeEach
    void setUp() {
        codec = new PlanningGovernanceSnapshotCodec(new ObjectMapper());
    }

    @Test
    void shouldHandleNullPayload() {
        PlanningGovernanceSnapshot snapshot = codec.readSnapshot(null);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.budgetDecision()).isNotNull();
    }

    @Test
    void shouldHandleEmptyPayload() {
        PlanningGovernanceSnapshot snapshot = codec.readSnapshot(Map.of());
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.budgetDecision()).isNotNull();
    }

    @Test
    void shouldEncodeAndDecode() {
        PlanningBudgetDecision budget = new PlanningBudgetDecision(true, "high_complex", 3, 2, true, false, "reason");
        PlanningGovernanceSnapshot snapshot = new PlanningGovernanceSnapshot(
                "v1",
                null, // complexity
                null, // planningMode
                null, // plannerModelRef
                null, // planningPolicyFingerprint
                null, // governancePolicyFingerprint
                budget, // budgetDecision
                null, // billingDecision
                null, // cacheDecision
                null  // rolloutDecision
        );

        Map<String, Object> payload = codec.toPayload(snapshot);
        assertThat(payload).isNotEmpty();

        PlanningGovernanceSnapshot decoded = codec.readSnapshot(payload);
        assertThat(decoded.budgetDecision().envelopeCode()).isEqualTo("high_complex");
        assertThat(decoded.previewContractVersion()).isEqualTo(com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview.CURRENT_CONTRACT_VERSION);
    }
}
