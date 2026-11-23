package com.arslanca.orchestrai.entity.concretes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChatPlanningSnapshotEntityTest {

    @Test
    void shouldCreateAndDeepCopySnapshotPayload() {
        final ChatEntity chat = ChatEntity.builder()
                .id(UUID.randomUUID())
                .title("Planning chat")
                .build();

        final ChatPlanningSnapshotEntity entity = ChatPlanningSnapshotEntity.create(
                chat,
                5L,
                "2.0",
                Map.of(
                        "structuredAnalysis", Map.of("projectName", "demo"),
                        "openQuestions", List.of("deployment model")));

        assertThat(entity.getChat()).isEqualTo(chat);
        assertThat(entity.getLastMessageCount()).isEqualTo(5L);
        assertThat(entity.getContractVersion()).isEqualTo("2.0");
        assertThat(entity.getSnapshotJson()).containsKey("structuredAnalysis");

        @SuppressWarnings("unchecked")
        final Map<String, Object> snapshotJson = entity.getSnapshotJson();
        ((Map<String, Object>) snapshotJson.get("structuredAnalysis")).put("projectName", "mutated");

        @SuppressWarnings("unchecked")
        final Map<String, Object> secondRead = (Map<String, Object>) entity.getSnapshotJson().get("structuredAnalysis");
        assertThat(secondRead).containsEntry("projectName", "demo");
    }
}
