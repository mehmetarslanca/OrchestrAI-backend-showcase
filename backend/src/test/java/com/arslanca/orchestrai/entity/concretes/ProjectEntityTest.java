package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.entity.enums.IdeTarget;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ProjectEntityTest {

    private ProjectEntity project;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = mock(UserEntity.class);
        project = ProjectEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .projectName("Test Project")
                .ideTarget(IdeTarget.GENERIC)
                .originalPrompt("Test prompt")
                .status(ProjectStatus.PENDING)
                .build();
    }

    @Test
    void shouldAllowTransitionFromPendingToFailed() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reason", "Early failure");

        project.markAsFailed(metadata);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.FAILED);
        assertThat(project.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void shouldAllowTransitionFromProcessingToFailed() {
        project.markAsProcessing(); // PENDING -> PROCESSING

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reason", "Processing failure");

        project.markAsFailed(metadata);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.FAILED);
        assertThat(project.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void shouldThrowExceptionWhenTransitioningFromCompletedToFailed() {
        project.markAsProcessing(); // PENDING -> PROCESSING
        project.markAsCompleted(new HashMap<>()); // PROCESSING -> COMPLETED

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reason", "Late failure");

        assertThatThrownBy(() -> project.markAsFailed(metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Geçersiz durum geçişi");
    }

    @Test
    void shouldThrowExceptionWhenTransitioningFromFailedToProcessing() {
        project.markAsFailed(new HashMap<>()); // PENDING -> FAILED

        assertThatThrownBy(() -> project.markAsProcessing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Geçersiz durum geçişi");
    }

    @Test
    void shouldPreserveResolvedHeavyModelMetadataWhenMarkingCompleted() {
        Map<String, Object> initialMetadata = new HashMap<>();
        initialMetadata.put("resolvedHeavyModel", Map.of("modelKey", "google:gemini-3-pro"));

        project = ProjectEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .projectName("Test Project")
                .ideTarget(IdeTarget.GENERIC)
                .originalPrompt("Test prompt")
                .status(ProjectStatus.PENDING)
                .metadata(initialMetadata)
                .build();

        project.markAsProcessing();
        project.markAsCompleted(Map.of("fileCount", 3));

        assertThat(project.getMetadata()).containsEntry("fileCount", 3);
        assertThat(project.getMetadata()).containsKey("resolvedHeavyModel");
    }
}
