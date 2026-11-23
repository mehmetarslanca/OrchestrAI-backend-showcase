package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.business.usage.AiUsageEventCommand;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageReconciliationStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSource;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_usage_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "pricing_snapshot_id")
    private UUID pricingSnapshotId;

    @Column(name = "request_id", length = 255)
    private String requestId;

    @Column(name = "provider_key", nullable = false, length = 50)
    private String providerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 50)
    private AiConnectionType connectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 20)
    private AiConnectionBillingMode billingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "surface", nullable = false, length = 30)
    private AiUsageSurface surface;

    @Column(name = "model_key", nullable = false, length = 120)
    private String modelKey;

    @Column(name = "concrete_model_id", length = 255)
    private String concreteModelId;

    @Column(name = "provider_request_id", length = 255)
    private String providerRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_source", nullable = false, length = 20)
    private AiUsageSource usageSource;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cached_input_tokens", nullable = false)
    private long cachedInputTokens;

    @Column(name = "reasoning_tokens", nullable = false)
    private long reasoningTokens;

    @Column(name = "estimated_cost_usd", precision = 18, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(name = "reconciled_cost_usd", precision = 18, scale = 8)
    private BigDecimal reconciledCostUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 30)
    private AiUsageReconciliationStatus reconciliationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiUsageEventStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static AiUsageEventEntity create(final AiUsageEventCommand command, final BigDecimal estimatedCostUsd) {
        final AiUsageEventEntity entity = new AiUsageEventEntity();
        entity.userId = command.userId();
        entity.connectionId = command.connectionId();
        entity.pricingSnapshotId = command.pricingSnapshotId();
        entity.requestId = command.requestId();
        entity.providerKey = command.providerKey();
        entity.connectionType = command.connectionType();
        entity.billingMode = command.billingMode();
        entity.surface = command.surface();
        entity.modelKey = command.modelKey();
        entity.concreteModelId = command.concreteModelId();
        entity.providerRequestId = command.providerRequestId();
        entity.usageSource = command.usageSource();
        entity.inputTokens = command.tokenUsage().inputTokens();
        entity.outputTokens = command.tokenUsage().outputTokens();
        entity.cachedInputTokens = command.tokenUsage().cachedInputTokens();
        entity.reasoningTokens = command.tokenUsage().reasoningTokens();
        entity.estimatedCostUsd = normalize(estimatedCostUsd);
        entity.reconciliationStatus = command.reconciliationStatus();
        entity.status = command.status();
        entity.startedAt = command.startedAt();
        entity.completedAt = command.completedAt();
        entity.metadata = copyMetadata(command.metadata());
        return entity;
    }

    public void reconcile(final BigDecimal reconciledCostUsd, final AiUsageReconciliationStatus reconciliationStatus) {
        if (reconciliationStatus == null) {
            throw new IllegalArgumentException("reconciliationStatus must be provided.");
        }
        if (reconciliationStatus == AiUsageReconciliationStatus.RECONCILED && reconciledCostUsd == null) {
            throw new IllegalArgumentException("reconciledCostUsd must be provided for RECONCILED events.");
        }
        if (reconciledCostUsd != null && reconciledCostUsd.signum() < 0) {
            throw new IllegalArgumentException("reconciledCostUsd cannot be negative.");
        }
        this.reconciledCostUsd = normalize(reconciledCostUsd);
        this.reconciliationStatus = reconciliationStatus;
    }

    private static BigDecimal normalize(final BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> copyMetadata(final Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(metadata));
    }
}
