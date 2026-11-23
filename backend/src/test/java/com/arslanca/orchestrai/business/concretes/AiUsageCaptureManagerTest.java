package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiPricingSnapshotService;
import com.arslanca.orchestrai.business.abstracts.AiUsageLedgerService;
import com.arslanca.orchestrai.business.runtime.AiUsageRuntimeDescriptor;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshot;
import com.arslanca.orchestrai.business.usage.AiUsageEventCommand;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageReconciliationStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSource;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiUsageCaptureManagerTest {

    @Mock
    private AiUsageLedgerService aiUsageLedgerService;

    @Mock
    private AiPricingSnapshotService aiPricingSnapshotService;

    @Test
    void shouldCaptureProviderUsageAndEmbeddedOpenAiPricingSnapshot() {
        final UUID snapshotId = UUID.randomUUID();
        when(aiPricingSnapshotService.captureSnapshot(any())).thenReturn(new AiPricingSnapshot(
                snapshotId,
                "openai",
                AiConnectionType.OPENAI_BYOK,
                AiConnectionBillingMode.BYOK,
                "gpt-4.1",
                "openai/gpt-4.1",
                "2026-06-12-embedded",
                new BigDecimal("10.00000000"),
                new BigDecimal("20.00000000"),
                new BigDecimal("1.00000000"),
                new BigDecimal("5.00000000"),
                Map.of(),
                OffsetDateTime.now()));

        final AiUsageCaptureManager manager = new AiUsageCaptureManager(aiUsageLedgerService, aiPricingSnapshotService);
        final ResolvedAiExecutionRoute route = new ResolvedAiExecutionRoute(
                AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                null,
                null,
                null,
                new AiUsageRuntimeDescriptor(
                        UUID.randomUUID(),
                        "openai",
                        AiConnectionType.OPENAI_BYOK,
                        AiConnectionBillingMode.BYOK,
                        "gpt-4.1",
                        "openai/gpt-4.1"));
        final Prompt prompt = new Prompt(List.of(new UserMessage("Build an API plan")));
        final ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Done"))),
                ChatResponseMetadata.builder()
                        .id("provider-request-1")
                        .model("openai/gpt-4.1")
                        .usage(new DefaultUsage(120, 40, 160))
                        .keyValue("cachedInputTokens", 20L)
                        .keyValue("reasoningTokens", 12L)
                        .build());

        manager.capture(
                UUID.randomUUID(),
                "corr-1",
                AiUsageSurface.PLANNING,
                AiUsageEventStatus.COMPLETED,
                route,
                prompt,
                "Done",
                response,
                OffsetDateTime.now().minusSeconds(2),
                OffsetDateTime.now(),
                Map.of("operation", "prompt-analysis"));

        final ArgumentCaptor<AiUsageEventCommand> captor = ArgumentCaptor.forClass(AiUsageEventCommand.class);
        verify(aiUsageLedgerService).recordEvent(captor.capture());
        final AiUsageEventCommand command = captor.getValue();
        assertThat(command.providerKey()).isEqualTo("openai");
        assertThat(command.connectionType()).isEqualTo(AiConnectionType.OPENAI_BYOK);
        assertThat(command.billingMode()).isEqualTo(AiConnectionBillingMode.BYOK);
        assertThat(command.usageSource()).isEqualTo(AiUsageSource.PROVIDER);
        assertThat(command.pricingSnapshotId()).isEqualTo(snapshotId);
        assertThat(command.reconciliationStatus()).isEqualTo(AiUsageReconciliationStatus.PENDING);
        assertThat(command.providerRequestId()).isEqualTo("provider-request-1");
        assertThat(command.tokenUsage().inputTokens()).isEqualTo(120L);
        assertThat(command.tokenUsage().outputTokens()).isEqualTo(40L);
        assertThat(command.tokenUsage().cachedInputTokens()).isEqualTo(20L);
        assertThat(command.tokenUsage().reasoningTokens()).isEqualTo(12L);
    }

    @Test
    void shouldMarkNonOpenAiRuntimeAsNotCostCapable() {
        final AiUsageCaptureManager manager = new AiUsageCaptureManager(aiUsageLedgerService, aiPricingSnapshotService);
        final ResolvedAiExecutionRoute route = new ResolvedAiExecutionRoute(
                AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                null,
                null,
                null,
                new AiUsageRuntimeDescriptor(
                        UUID.randomUUID(),
                        "github",
                        AiConnectionType.GITHUB_MODELS_USER_LINKED,
                        AiConnectionBillingMode.USER_LINKED,
                        "gpt-4.1",
                        "openai/gpt-4.1"));
        final Prompt prompt = new Prompt(List.of(new UserMessage("Summarize this chat")));
        final ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Summary"))),
                ChatResponseMetadata.builder()
                        .id("provider-request-2")
                        .usage(new DefaultUsage(80, 12, 92))
                        .build());

        manager.capture(
                UUID.randomUUID(),
                "corr-2",
                AiUsageSurface.INTERNAL_LIGHT_TASK,
                AiUsageEventStatus.COMPLETED,
                route,
                prompt,
                "Summary",
                response,
                OffsetDateTime.now().minusSeconds(1),
                OffsetDateTime.now(),
                Map.of("operation", "chat-title-suggestion"));

        final ArgumentCaptor<AiUsageEventCommand> captor = ArgumentCaptor.forClass(AiUsageEventCommand.class);
        verify(aiUsageLedgerService).recordEvent(captor.capture());
        final AiUsageEventCommand command = captor.getValue();
        assertThat(command.providerKey()).isEqualTo("github");
        assertThat(command.pricingSnapshotId()).isNull();
        assertThat(command.reconciliationStatus()).isEqualTo(AiUsageReconciliationStatus.NOT_SUPPORTED);
        assertThat(command.usageSource()).isEqualTo(AiUsageSource.PROVIDER);
        assertThat(command.metadata()).containsEntry("estimatedCostState", "NOT_COST_CAPABLE");
    }
}
