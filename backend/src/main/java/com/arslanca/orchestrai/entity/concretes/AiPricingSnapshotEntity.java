package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.business.usage.AiPricingSnapshotCommand;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
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
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_pricing_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiPricingSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider_key", nullable = false, length = 50)
    private String providerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 50)
    private AiConnectionType connectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 20)
    private AiConnectionBillingMode billingMode;

    @Column(name = "model_key", nullable = false, length = 120)
    private String modelKey;

    @Column(name = "concrete_model_id", length = 255)
    private String concreteModelId;

    @Column(name = "rate_card_version", length = 80)
    private String rateCardVersion;

    @Column(name = "input_cost_per_million_usd", precision = 18, scale = 8)
    private BigDecimal inputCostPerMillionUsd;

    @Column(name = "output_cost_per_million_usd", precision = 18, scale = 8)
    private BigDecimal outputCostPerMillionUsd;

    @Column(name = "cached_input_cost_per_million_usd", precision = 18, scale = 8)
    private BigDecimal cachedInputCostPerMillionUsd;

    @Column(name = "reasoning_cost_per_million_usd", precision = 18, scale = 8)
    private BigDecimal reasoningCostPerMillionUsd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private OffsetDateTime capturedAt;

    public static AiPricingSnapshotEntity create(final AiPricingSnapshotCommand command) {
        final AiPricingSnapshotEntity entity = new AiPricingSnapshotEntity();
        entity.providerKey = command.providerKey();
        entity.connectionType = command.connectionType();
        entity.billingMode = command.billingMode();
        entity.modelKey = command.modelKey();
        entity.concreteModelId = command.concreteModelId();
        entity.rateCardVersion = command.rateCardVersion();
        entity.inputCostPerMillionUsd = normalize(command.inputCostPerMillionUsd());
        entity.outputCostPerMillionUsd = normalize(command.outputCostPerMillionUsd());
        entity.cachedInputCostPerMillionUsd = normalize(command.cachedInputCostPerMillionUsd());
        entity.reasoningCostPerMillionUsd = normalize(command.reasoningCostPerMillionUsd());
        entity.metadata = copyMetadata(command.metadata());
        return entity;
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
