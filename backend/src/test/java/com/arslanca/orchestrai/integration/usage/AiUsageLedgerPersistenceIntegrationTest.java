package com.arslanca.orchestrai.integration.usage;

import com.arslanca.orchestrai.business.abstracts.AiPricingSnapshotService;
import com.arslanca.orchestrai.business.abstracts.AiUsageLedgerService;
import com.arslanca.orchestrai.business.concretes.AiPricingSnapshotManager;
import com.arslanca.orchestrai.business.concretes.AiUsageLedgerManager;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshot;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshotCommand;
import com.arslanca.orchestrai.business.usage.AiTokenUsage;
import com.arslanca.orchestrai.business.usage.AiUsageEventCommand;
import com.arslanca.orchestrai.business.usage.AiUsageSpendSummary;
import com.arslanca.orchestrai.dataaccess.abstracts.AiUsageEventRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageReconciliationStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSource;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import({AiPricingSnapshotManager.class, AiUsageLedgerManager.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AiUsageLedgerPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("orchestrai")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AiPricingSnapshotService aiPricingSnapshotService;

    @Autowired
    private AiUsageLedgerService aiUsageLedgerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAiConnectionRepository userAiConnectionRepository;

    @Autowired
    private AiUsageEventRepository aiUsageEventRepository;

    @Test
    void shouldPersistPricingSnapshotsAndSummarizeUsageLedgerSpend() {
        final UserEntity user = userRepository.save(UserEntity.builder()
                .githubId(Math.abs(UUID.randomUUID().getMostSignificantBits()))
                .username("usage-ledger-user")
                .email("usage-ledger-" + UUID.randomUUID() + "@example.com")
                .build());
        final UserAiConnectionEntity connection = userAiConnectionRepository.save(
                UserAiConnectionEntity.createPendingByok(user.getId(), AiConnectionType.OPENAI_BYOK, "openai", 1));

        final AiPricingSnapshot snapshot = aiPricingSnapshotService.captureSnapshot(new AiPricingSnapshotCommand(
                "openai",
                AiConnectionType.OPENAI_BYOK,
                AiConnectionBillingMode.BYOK,
                "gpt-4.1",
                "openai/gpt-4.1",
                "2026-06-12",
                new BigDecimal("10.00000000"),
                new BigDecimal("20.00000000"),
                new BigDecimal("1.00000000"),
                new BigDecimal("5.00000000"),
                Map.of("source", "integration-test")));

        assertThat(snapshot.id()).isNotNull();
        assertThat(snapshot.capturedAt()).isNotNull();

        final OffsetDateTime startedAt = OffsetDateTime.now().minusMinutes(1);
        final UUID eventId = aiUsageLedgerService.recordEvent(new AiUsageEventCommand(
                user.getId(),
                connection.getId(),
                snapshot.id(),
                "req-123",
                "openai",
                AiConnectionType.OPENAI_BYOK,
                AiConnectionBillingMode.BYOK,
                AiUsageSurface.PLANNING,
                "gpt-4.1",
                "openai/gpt-4.1",
                "provider-req-123",
                AiUsageSource.ESTIMATED,
                new AiTokenUsage(1_000, 2_000, 500, 250),
                null,
                AiUsageEventStatus.COMPLETED,
                startedAt,
                null,
                Map.of("phase", "slice-1")));

        assertThat(aiUsageEventRepository.findById(eventId))
                .get()
                .extracting(event -> event.getEstimatedCostUsd().toPlainString(), event -> event.getPricingSnapshotId())
                .containsExactly("0.05175000", snapshot.id());

        aiUsageLedgerService.reconcileEvent(eventId, new BigDecimal("0.04950000"), AiUsageReconciliationStatus.RECONCILED);

        final AiUsageSpendSummary spendSummary = aiUsageLedgerService.summarizeConnectionSpend(
                user.getId(),
                connection.getId(),
                startedAt.minusMinutes(1),
                startedAt.plusMinutes(5));

        assertThat(spendSummary.estimatedCostUsd()).isEqualByComparingTo("0.05175000");
        assertThat(spendSummary.reconciledCostUsd()).isEqualByComparingTo("0.04950000");
        assertThat(spendSummary.eventCount()).isEqualTo(1);
        assertThat(spendSummary.lastRecordedAt()).isNotNull();

        assertThat(aiUsageLedgerService.getConnectionHealthSummary(user.getId(), connection.getId()))
                .get()
                .satisfies(summary -> {
                    assertThat(summary.connectionId()).isEqualTo(connection.getId());
                    assertThat(summary.providerKey()).isEqualTo("openai");
                    assertThat(summary.lastSuccessfulUsageAt()).isNotNull();
                    assertThat(summary.lastSuccessfulSyncAt()).isNotNull();
                    assertThat(summary.reauthRequired()).isFalse();
                });
    }
}
