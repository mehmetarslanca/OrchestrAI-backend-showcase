package com.arslanca.orchestrai.business.planning.preparation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.orchestration.LifecycleStage;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary;
import com.arslanca.orchestrai.business.dtos.orchestration.RequirementCompletenessReport;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.entity.concretes.ChatPlanningSnapshotEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PreparationSnapshotCodec {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public PreparationSnapshotCodec(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StructuredAnalysis readStructuredAnalysis(final ProjectGenerationPreparationEntity preparation) {
        return objectMapper.convertValue(preparation.snapshotStructuredAnalysis(), StructuredAnalysis.class);
    }

    public StarterSelection readStarterSelection(final ProjectGenerationPreparationEntity preparation) {
        return objectMapper.convertValue(preparation.snapshotStarterSelection(), StarterSelection.class);
    }

    public PlanningSummary readPlanningSummary(final ProjectGenerationPreparationEntity preparation) {
        return readPlanningSummary(preparation.snapshotPreparationSnapshot());
    }

    public ResolvedHeavyModel readResolvedHeavyModel(final ProjectGenerationPreparationEntity preparation) {
        return readResolvedHeavyModel(preparation.snapshotPreparationSnapshot());
    }

    public PlanningSummary readPlanningSummary(final Map<String, Object> preparationSnapshot) {
        if (preparationSnapshot == null || preparationSnapshot.isEmpty()) {
            return null;
        }
        final Object planningSummaryPayload = preparationSnapshot.get("planningSummary");
        if (planningSummaryPayload == null) {
            return null;
        }
        return objectMapper.convertValue(planningSummaryPayload, PlanningSummary.class);
    }

    public ResolvedHeavyModel readResolvedHeavyModel(final Map<String, Object> preparationSnapshot) {
        if (preparationSnapshot == null || preparationSnapshot.isEmpty()) {
            return null;
        }
        final Object resolvedHeavyModelPayload = preparationSnapshot.get("resolvedHeavyModel");
        if (resolvedHeavyModelPayload == null) {
            return null;
        }
        return objectMapper.convertValue(resolvedHeavyModelPayload, ResolvedHeavyModel.class);
    }

    public ChatPlanningSnapshot readChatPlanningSnapshot(final ChatPlanningSnapshotEntity entity) {
        if (entity == null || entity.getSnapshotJson() == null || entity.getSnapshotJson().isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(entity.getSnapshotJson(), ChatPlanningSnapshot.class);
    }

    public Map<String, Object> toPayload(final Object payload) {
        return objectMapper.convertValue(payload, MAP_TYPE);
    }

    public Map<String, Object> buildPreparationSnapshot(
            final StructuredAnalysis analysis,
            final PlanningSummary planningSummary,
            final StarterSelection starterSelection,
            final RequirementCompletenessReport completenessReport,
            final Optional<ChatPlanningSnapshot> chatPlanningSnapshot,
            final Long chatMessageCount) {
        return buildPreparationSnapshot(
                analysis,
                planningSummary,
                starterSelection,
                completenessReport,
                null,
                chatPlanningSnapshot,
                chatMessageCount);
    }

    public Map<String, Object> buildPreparationSnapshot(
            final StructuredAnalysis analysis,
            final PlanningSummary planningSummary,
            final StarterSelection starterSelection,
            final RequirementCompletenessReport completenessReport,
            final ResolvedHeavyModel resolvedHeavyModel,
            final Optional<ChatPlanningSnapshot> chatPlanningSnapshot,
            final Long chatMessageCount) {
        final Map<String, Object> snapshotPayload = new LinkedHashMap<>();
        snapshotPayload.put("contractVersion", analysis == null ? planningSummary.contractVersion() : analysis.contractVersion());
        snapshotPayload.put("lifecycleStage", LifecycleStage.PREPARE.value());
        snapshotPayload.put("structuredAnalysis", toPayload(analysis));
        snapshotPayload.put("planningSummary", toPayload(planningSummary));
        snapshotPayload.put("starterSelection", toPayload(starterSelection));
        snapshotPayload.put("requirementCompleteness", toPayload(completenessReport));
        if (resolvedHeavyModel != null) {
            snapshotPayload.put("resolvedHeavyModel", toPayload(resolvedHeavyModel));
        }
        chatPlanningSnapshot.ifPresent(snapshot -> snapshotPayload.put("chatPlanningSnapshot", toPayload(snapshot)));
        if (chatMessageCount != null) {
            snapshotPayload.put("chatPlanningSnapshotMessageCount", chatMessageCount);
        }
        return snapshotPayload;
    }
}
