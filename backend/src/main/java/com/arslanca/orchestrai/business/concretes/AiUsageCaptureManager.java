package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiPricingSnapshotService;
import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.abstracts.AiUsageLedgerService;
import com.arslanca.orchestrai.business.runtime.AiUsageRuntimeDescriptor;
import com.arslanca.orchestrai.business.runtime.AiUsageRuntimeDescriptors;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshot;
import com.arslanca.orchestrai.business.usage.AiPricingSnapshotCommand;
import com.arslanca.orchestrai.business.usage.AiTokenUsage;
import com.arslanca.orchestrai.business.usage.AiUsageEventCommand;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageReconciliationStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSource;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiUsageCaptureManager implements AiUsageCaptureService {

    private static final String RATE_CARD_VERSION = "2026-06-12-embedded";

    private static final Map<String, OpenAiRateCard> OPENAI_RATE_CARD_BY_MODEL_KEY = Map.of(
            "gpt-4.1", new OpenAiRateCard("gpt-4.1",
                    new BigDecimal("10.00000000"),
                    new BigDecimal("20.00000000"),
                    new BigDecimal("1.00000000"),
                    new BigDecimal("5.00000000")),
            "gpt-4.1-mini", new OpenAiRateCard("gpt-4.1-mini",
                    new BigDecimal("2.00000000"),
                    new BigDecimal("8.00000000"),
                    new BigDecimal("0.50000000"),
                    new BigDecimal("2.00000000")),
            "o4-mini", new OpenAiRateCard("o4-mini",
                    new BigDecimal("4.00000000"),
                    new BigDecimal("16.00000000"),
                    new BigDecimal("1.00000000"),
                    new BigDecimal("8.00000000")));

    private final AiUsageLedgerService aiUsageLedgerService;
    private final AiPricingSnapshotService aiPricingSnapshotService;

    @Override
    public void capture(
            final UUID userId,
            final String requestId,
            final AiUsageSurface surface,
            final AiUsageEventStatus status,
            final ResolvedAiExecutionRoute route,
            final Prompt prompt,
            final String responseText,
            final ChatResponse response,
            final OffsetDateTime startedAt,
            final OffsetDateTime completedAt,
            final Map<String, Object> metadata) {
        if (userId == null) {
            log.debug("Skipping AI usage capture because userId is unavailable: surface={}, status={}", surface, status);
            return;
        }
        if (route == null || prompt == null || surface == null || status == null || startedAt == null) {
            throw new IllegalArgumentException("AI usage capture requires route, prompt, surface, status and startedAt.");
        }

        final AiUsageRuntimeDescriptor runtimeDescriptor = requireRuntimeDescriptor(route);
        final UsageObservation usageObservation = usageObservation(prompt, responseText, response);
        final PricingResolution pricingResolution = resolvePricing(runtimeDescriptor);
        final Map<String, Object> eventMetadata = buildMetadata(
                route,
                runtimeDescriptor,
                response,
                prompt,
                responseText,
                metadata,
                pricingResolution);

        aiUsageLedgerService.recordEvent(new AiUsageEventCommand(
                userId,
                runtimeDescriptor.connectionId(),
                pricingResolution.pricingSnapshotId(),
                requestId,
                runtimeDescriptor.providerKey(),
                runtimeDescriptor.connectionType(),
                runtimeDescriptor.billingMode(),
                surface,
                runtimeDescriptor.modelKey(),
                runtimeDescriptor.concreteModelId(),
                usageObservation.providerRequestId(),
                usageObservation.usageSource(),
                usageObservation.tokenUsage(),
                pricingResolution.reconciliationStatus(),
                status,
                startedAt,
                completedAt == null ? OffsetDateTime.now() : completedAt,
                eventMetadata));
    }

    private AiUsageRuntimeDescriptor requireRuntimeDescriptor(final ResolvedAiExecutionRoute route) {
        if (route.runtimeDescriptor() != null) {
            return route.runtimeDescriptor();
        }
        if (route.resolvedHeavyModel() != null) {
            return AiUsageRuntimeDescriptors.forResolvedHeavyModel(route.resolvedHeavyModel(), route.providerCredential());
        }
        throw new IllegalStateException("Resolved AI execution route does not expose runtime descriptor metadata.");
    }

    private AiConnectionBillingMode billingMode(final ResolvedAiExecutionRoute route) {
        if (route.providerCredential() != null && route.providerCredential().source() != null) {
            return switch (route.providerCredential().source()) {
                case PLATFORM -> AiConnectionBillingMode.PLATFORM_PAID;
                case BYOK -> AiConnectionBillingMode.BYOK;
                case USER_LINKED -> AiConnectionBillingMode.USER_LINKED;
            };
        }
        return AiConnectionBillingMode.PLATFORM_PAID;
    }

    private UsageObservation usageObservation(
            final Prompt prompt,
            final String responseText,
            final ChatResponse response) {
        final ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        final Usage usage = metadata == null ? null : metadata.getUsage();
        final Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        final Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        final long cachedInputTokens = longMetadata(metadata, "cachedInputTokens");
        final long reasoningTokens = longMetadata(metadata, "reasoningTokens");
        final boolean providerUsageAvailable = positive(promptTokens) || positive(completionTokens)
                || cachedInputTokens > 0
                || reasoningTokens > 0;
        if (providerUsageAvailable) {
            return new UsageObservation(
                    new AiTokenUsage(nonNegative(promptTokens), nonNegative(completionTokens), cachedInputTokens, reasoningTokens),
                    AiUsageSource.PROVIDER,
                    metadata == null ? null : metadata.getId());
        }
        return new UsageObservation(
                new AiTokenUsage(estimateTokens(promptChars(prompt)), estimateTokens(textLength(responseText)), 0L, 0L),
                AiUsageSource.ESTIMATED,
                metadata == null ? null : metadata.getId());
    }

    private PricingResolution resolvePricing(final AiUsageRuntimeDescriptor runtimeDescriptor) {
        if (!supportsOpenAiEstimatedCost(runtimeDescriptor)) {
            return new PricingResolution(null, AiUsageReconciliationStatus.NOT_SUPPORTED, "NOT_COST_CAPABLE");
        }
        final OpenAiRateCard rateCard = OPENAI_RATE_CARD_BY_MODEL_KEY.get(runtimeDescriptor.modelKey());
        if (rateCard == null) {
            return new PricingResolution(null, AiUsageReconciliationStatus.PENDING, "RATE_CARD_UNAVAILABLE");
        }
        try {
            final AiPricingSnapshot snapshot = aiPricingSnapshotService.captureSnapshot(new AiPricingSnapshotCommand(
                    runtimeDescriptor.providerKey(),
                    runtimeDescriptor.connectionType(),
                    runtimeDescriptor.billingMode(),
                    runtimeDescriptor.modelKey(),
                    runtimeDescriptor.concreteModelId(),
                    RATE_CARD_VERSION,
                    rateCard.inputCostPerMillionUsd(),
                    rateCard.outputCostPerMillionUsd(),
                    rateCard.cachedInputCostPerMillionUsd(),
                    rateCard.reasoningCostPerMillionUsd(),
                    Map.of("catalogSource", "embedded-openai-rate-card")));
            return new PricingResolution(snapshot.id(), AiUsageReconciliationStatus.PENDING, "SNAPSHOT_CAPTURED");
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to capture AI pricing snapshot: provider={}, modelKey={}, reason={}",
                    runtimeDescriptor.providerKey(),
                    runtimeDescriptor.modelKey(),
                    exception.getMessage());
            return new PricingResolution(null, AiUsageReconciliationStatus.PENDING, "SNAPSHOT_CAPTURE_FAILED");
        }
    }

    private boolean supportsOpenAiEstimatedCost(final AiUsageRuntimeDescriptor runtimeDescriptor) {
        if (!"openai".equals(runtimeDescriptor.providerKey())) {
            return false;
        }
        return runtimeDescriptor.connectionType() == AiConnectionType.OPENAI_BYOK
                || runtimeDescriptor.connectionType() == AiConnectionType.OPENAI_APP_PAID;
    }

    private Map<String, Object> buildMetadata(
            final ResolvedAiExecutionRoute route,
            final AiUsageRuntimeDescriptor runtimeDescriptor,
            final ChatResponse response,
            final Prompt prompt,
            final String responseText,
            final Map<String, Object> metadata,
            final PricingResolution pricingResolution) {
        final Map<String, Object> eventMetadata = new LinkedHashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            eventMetadata.putAll(metadata);
        }
        putIfPresent(eventMetadata, "executionClass", route.executionClass() == null ? null : route.executionClass().name());
        putIfPresent(eventMetadata, "runtimeProviderKey", runtimeDescriptor.providerKey());
        putIfPresent(eventMetadata, "runtimeConnectionType", runtimeDescriptor.connectionType().name());
        putIfPresent(eventMetadata, "runtimeBillingMode", runtimeDescriptor.billingMode().name());
        putIfPresent(eventMetadata, "runtimeModelKey", runtimeDescriptor.modelKey());
        putIfPresent(eventMetadata, "runtimeConcreteModelId", runtimeDescriptor.concreteModelId());
        eventMetadata.put("promptChars", promptChars(prompt));
        eventMetadata.put("responseChars", textLength(responseText));
        putIfPresent(eventMetadata, "estimatedCostState", pricingResolution.estimatedCostState());
        if (route.resolvedHeavyModel() != null) {
            putIfPresent(eventMetadata, "selectedRouteProviderKey", route.resolvedHeavyModel().provider());
            putIfPresent(eventMetadata, "selectedRouteModelKey", route.resolvedHeavyModel().modelKey());
            putIfPresent(eventMetadata, "selectedRouteFamily", route.resolvedHeavyModel().family());
            putIfPresent(eventMetadata, "selectedRouteConcreteModelId", route.resolvedHeavyModel().concreteModelId());
            putIfPresent(eventMetadata, "selectedRouteConnectionId",
                    route.resolvedHeavyModel().selectedConnectionId() == null ? null : route.resolvedHeavyModel().selectedConnectionId().toString());
        }
        if (response != null && response.getMetadata() != null) {
            putIfPresent(eventMetadata, "providerReportedModel", response.getMetadata().getModel());
        }
        return Map.copyOf(eventMetadata);
    }

    private int promptChars(final Prompt prompt) {
        int total = 0;
        for (org.springframework.ai.chat.messages.Message instruction : prompt.getInstructions()) {
            total += textLength(instruction == null ? null : instruction.getText());
        }
        return total;
    }

    private int textLength(final String value) {
        return value == null ? 0 : value.length();
    }

    private long estimateTokens(final int charCount) {
        if (charCount <= 0) {
            return 0L;
        }
        return Math.max(1L, (charCount + 3L) / 4L);
    }

    private long longMetadata(final ChatResponseMetadata metadata, final String key) {
        if (metadata == null || key == null || key.isBlank() || !metadata.containsKey(key)) {
            return 0L;
        }
        final Object value = metadata.get(key);
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value instanceof String text) {
            try {
                return Math.max(0L, Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private boolean positive(final Integer value) {
        return value != null && value > 0;
    }

    private long nonNegative(final Integer value) {
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    private void putIfPresent(final Map<String, Object> metadata, final String key, final Object value) {
        if (key != null && value != null) {
            metadata.put(key, value);
        }
    }

    private record UsageObservation(
            AiTokenUsage tokenUsage,
            AiUsageSource usageSource,
            String providerRequestId) {
    }

    private record PricingResolution(
            UUID pricingSnapshotId,
            AiUsageReconciliationStatus reconciliationStatus,
            String estimatedCostState) {
    }

    private record OpenAiRateCard(
            String modelKey,
            BigDecimal inputCostPerMillionUsd,
            BigDecimal outputCostPerMillionUsd,
            BigDecimal cachedInputCostPerMillionUsd,
            BigDecimal reasoningCostPerMillionUsd) {
    }
}
