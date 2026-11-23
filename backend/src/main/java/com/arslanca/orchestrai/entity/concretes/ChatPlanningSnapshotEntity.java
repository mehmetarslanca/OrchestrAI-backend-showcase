package com.arslanca.orchestrai.entity.concretes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.arslanca.orchestrai.business.enums.ChatPlanningReadinessStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "chat_planning_snapshots")
public class ChatPlanningSnapshotEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatEntity chat;

    @Column(name = "last_summarized_message_count", nullable = false)
    private long lastSummarizedMessageCount;

    @Column(name = "snapshot_contract_version", nullable = false, length = 20)
    private String snapshotContractVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "jsonb")
    private Map<String, Object> snapshotJson;

    @Column(name = "last_evaluated_message_count", nullable = false)
    private long lastEvaluatedMessageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "planning_status", nullable = false, length = 40)
    private ChatPlanningReadinessStatus planningStatus;

    @Column(name = "planning_status_reason", columnDefinition = "TEXT")
    private String planningStatusReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    protected ChatPlanningSnapshotEntity() {
    }

    public static ChatPlanningSnapshotEntity create(
            final ChatEntity chat,
            final long lastSummarizedMessageCount,
            final String snapshotContractVersion,
            final Map<String, Object> snapshotJson) {
        final ChatPlanningSnapshotEntity entity = new ChatPlanningSnapshotEntity();
        entity.id = UUID.randomUUID();
        entity.chat = chat;
        entity.lastSummarizedMessageCount = lastSummarizedMessageCount;
        entity.snapshotContractVersion = snapshotContractVersion == null || snapshotContractVersion.isBlank()
                ? "1.0"
                : snapshotContractVersion.trim();
        entity.snapshotJson = deepCopyMap(snapshotJson);
        entity.lastEvaluatedMessageCount = 0L;
        entity.planningStatus = ChatPlanningReadinessStatus.DISCOVERY_IN_PROGRESS;
        entity.planningStatusReason = null;
        entity.version = 0L;
        return entity;
    }

    @PrePersist
    protected void onPrePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        final OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public ChatEntity getChat() {
        return chat;
    }

    public long getLastMessageCount() {
        return lastSummarizedMessageCount;
    }

    public String getContractVersion() {
        return snapshotContractVersion;
    }

    public Map<String, Object> getSnapshotJson() {
        return deepCopyMap(snapshotJson);
    }

    public long getLastEvaluatedMessageCount() {
        return lastEvaluatedMessageCount;
    }

    public ChatPlanningReadinessStatus getPlanningStatus() {
        return planningStatus;
    }

    public String getPlanningStatusReason() {
        return planningStatusReason;
    }

    public void updateSnapshot(
            final long lastSummarizedMessageCount,
            final String snapshotContractVersion,
            final Map<String, Object> snapshotJson) {
        this.lastSummarizedMessageCount = lastSummarizedMessageCount;
        this.snapshotContractVersion = snapshotContractVersion == null || snapshotContractVersion.isBlank()
                ? this.snapshotContractVersion
                : snapshotContractVersion.trim();
        this.snapshotJson = deepCopyMap(snapshotJson);
    }

    public void updateEvaluation(
            final long lastEvaluatedMessageCount,
            final ChatPlanningReadinessStatus planningStatus,
            final String planningStatusReason) {
        this.lastEvaluatedMessageCount = lastEvaluatedMessageCount;
        this.planningStatus = planningStatus == null
                ? ChatPlanningReadinessStatus.DISCOVERY_IN_PROGRESS
                : planningStatus;
        this.planningStatusReason = planningStatusReason == null || planningStatusReason.isBlank()
                ? null
                : planningStatusReason.trim();
    }

    public void markAnalysisRequested(final long lastEvaluatedMessageCount, final String planningStatusReason) {
        updateEvaluation(lastEvaluatedMessageCount, ChatPlanningReadinessStatus.ANALYZING, planningStatusReason);
    }

    private static Map<String, Object> deepCopyMap(final Map<String, Object> original) {
        if (original == null) {
            return null;
        }
        final Map<String, Object> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : original.entrySet()) {
            copy.put(entry.getKey(), deepCopyObject(entry.getValue()));
        }
        return copy;
    }

    private static Object deepCopyObject(final Object obj) {
        if (obj instanceof Map<?, ?> map) {
            final Map<String, Object> copyMap = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copyMap.put(key, deepCopyObject(entry.getValue()));
                }
            }
            return copyMap;
        }
        if (obj instanceof java.util.List<?> list) {
            final java.util.List<Object> copyList = new java.util.ArrayList<>();
            for (final Object item : list) {
                copyList.add(deepCopyObject(item));
            }
            return copyList;
        }
        return obj;
    }
}
