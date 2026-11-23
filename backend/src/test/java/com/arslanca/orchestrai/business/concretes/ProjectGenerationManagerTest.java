package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.PreparedProjectGenerationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.ProjectPlanningPreviewService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactData;
import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactResponse;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;
import com.arslanca.orchestrai.business.mapper.EntityMapper;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.exception.type.PreparationStateException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectArtifactRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectArtifactEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectGenerationManagerTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectArtifactRepository projectArtifactRepository;
    @Mock private EntityMapper entityMapper;
    @Mock private ProjectGenerationPreparationService preparationService;
    @Mock private ProjectPlanningPreviewService planningPreviewService;
    @Mock private PreparedProjectGenerationService preparedGenerationService;
    @Mock private PlanningLifecycleAuthority lifecycleAuthority;

    @InjectMocks
    private ProjectGenerationManager projectGenerationManager;

    private UUID userId;
    private SseEventPublisher publisher;

    @Captor
    private ArgumentCaptor<GenerationPreparationRequest> prepRequestCaptor;

    @Captor
    private ArgumentCaptor<GenerationStartRequest> startRequestCaptor;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        publisher = mock(SseEventPublisher.class);
    }

    @Test
    void shouldLoadArtifactOnlyForOwningUser() {
        UUID projectId = UUID.randomUUID();
        ProjectArtifactEntity entity = mock(ProjectArtifactEntity.class);
        ProjectArtifactResponse response = new ProjectArtifactResponse(
                projectId,
                new ProjectArtifactData("v2", java.util.List.of()),
                null,
                null);

        when(projectArtifactRepository.findByProjectIdAndProject_User_Id(projectId, userId)).thenReturn(java.util.Optional.of(entity));
        when(entityMapper.toProjectArtifactResponse(entity)).thenReturn(response);

        assertThat(projectGenerationManager.getProjectArtifact(userId, projectId)).isEqualTo(response);
        verify(projectArtifactRepository).findByProjectIdAndProject_User_Id(projectId, userId);
    }

    @Nested
    class GenerateBlueprintTests {
        
        @Test
        void shouldMapPropertiesAndDelegateToPreparedManager() {
            // Arrange
            ProjectRequest request = new ProjectRequest(
                    "TestProject",
                    "cursor",
                    "Do a thing",
                    "myModel",
                    CredentialSource.PLATFORM
            );

            UUID prepId = UUID.randomUUID();
            GenerationPreparationResponse prepResponse = new GenerationPreparationResponse(
                    prepId, "TestProject", "cursor", null, null, false, null
            );

            when(preparationService.prepareGeneration(eq(userId), any(GenerationPreparationRequest.class)))
                    .thenReturn(prepResponse);
            when(lifecycleAuthority.requiresAcceptedPreview(prepResponse)).thenReturn(false);

            UUID expectedProjectId = UUID.randomUUID();
            when(preparedGenerationService.startGenerationWithOverride(
                    eq(userId),
                    any(GenerationStartRequest.class),
                    eq("myModel"),
                    eq(CredentialSource.PLATFORM),
                    eq(publisher)
            )).thenReturn(expectedProjectId);

            // Act
            UUID resultId = projectGenerationManager.generateBlueprint(userId, request, publisher);

            // Assert
            assertThat(resultId).isEqualTo(expectedProjectId);
            
            // Verify preparation mapping
            verify(preparationService).prepareGeneration(eq(userId), prepRequestCaptor.capture());
            GenerationPreparationRequest capturedPrepRequest = prepRequestCaptor.getValue();
            assertThat(capturedPrepRequest.projectName()).isEqualTo(request.projectName());
            assertThat(capturedPrepRequest.ideTarget()).isEqualTo(request.ideTarget());
            assertThat(capturedPrepRequest.prompt()).isEqualTo(request.prompt());
            assertThat(capturedPrepRequest.sourceChannel()).isNull();

            // Verify start mapping
            verify(preparedGenerationService).startGenerationWithOverride(
                    eq(userId),
                    startRequestCaptor.capture(),
                    eq(request.heavyModelKey()),
                    eq(request.heavyModelAuthSource()),
                    eq(publisher)
            );
            GenerationStartRequest capturedStartRequest = startRequestCaptor.getValue();
            assertThat(capturedStartRequest.preparationId()).isEqualTo(prepId);
            assertThat(capturedStartRequest.starterKey()).isNull();
            assertThat(capturedStartRequest.previewId()).isNull();
        }

        @Test
        void shouldBridgePreviewLifecycleWhenPreparationRequiresAcceptedPreview() {
            ProjectRequest request = new ProjectRequest(
                    "ComplexProject",
                    "cursor",
                    "Do a complex thing",
                    "myModel",
                    CredentialSource.PLATFORM
            );

            UUID prepId = UUID.randomUUID();
            UUID previewId = UUID.randomUUID();
            GenerationPreparationResponse prepResponse = new GenerationPreparationResponse(
                    prepId,
                    "ComplexProject",
                    "cursor",
                    null,
                    null,
                    false,
                    null,
                    new com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary(
                            com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity.COMPLEX,
                            java.util.List.of("multi-tenant"),
                            "saas | frontend + backend",
                            com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                            "elevated",
                            true,
                            true,
                            false,
                            "starter",
                            "google:gemini-3-pro",
                            com.arslanca.orchestrai.business.dtos.ai.ModelCapability.HIGH,
                            false,
                            "none",
                            com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary.CURRENT_CONTRACT_VERSION)
            );
            PlanningPreview preview = new PlanningPreview(
                    previewId,
                    prepId,
                    "nextjs-saas-shell",
                    com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                    PlanningPreviewStatus.READY_FOR_REVIEW,
                    java.util.Map.of(),
                    java.util.Map.of(),
                    PlanningPreview.CURRENT_CONTRACT_VERSION,
                    null
            );
            PlanningPreview acceptedPreview = new PlanningPreview(
                    previewId,
                    prepId,
                    "nextjs-saas-shell",
                    com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                    PlanningPreviewStatus.ACCEPTED,
                    java.util.Map.of(),
                    java.util.Map.of(),
                    PlanningPreview.CURRENT_CONTRACT_VERSION,
                    null
            );

            when(preparationService.prepareGeneration(eq(userId), any(GenerationPreparationRequest.class)))
                    .thenReturn(prepResponse);
            when(lifecycleAuthority.requiresAcceptedPreview(prepResponse)).thenReturn(true);
            when(planningPreviewService.generatePreview(eq(userId), any(GenerationPreviewRequest.class)))
                    .thenReturn(preview);
            when(lifecycleAuthority.previewRequiresAcceptance(preview)).thenReturn(true);
            when(planningPreviewService.acceptPreview(userId, previewId)).thenReturn(acceptedPreview);
            when(preparedGenerationService.startGenerationWithOverride(
                    eq(userId),
                    any(GenerationStartRequest.class),
                    eq("myModel"),
                    eq(CredentialSource.PLATFORM),
                    eq(publisher)
            )).thenReturn(UUID.randomUUID());

            projectGenerationManager.generateBlueprint(userId, request, publisher);

            verify(planningPreviewService).generatePreview(eq(userId), any(GenerationPreviewRequest.class));
            verify(planningPreviewService).acceptPreview(userId, previewId);
            verify(preparedGenerationService).startGenerationWithOverride(
                    eq(userId),
                    startRequestCaptor.capture(),
                    eq(request.heavyModelKey()),
                    eq(request.heavyModelAuthSource()),
                    eq(publisher)
            );
            assertThat(startRequestCaptor.getValue().previewId()).isEqualTo(previewId);
        }

        @Test
        void shouldRejectLegacyAutoAcceptWhenGeneratedPreviewFailsQualityGate() {
            ProjectRequest request = new ProjectRequest(
                    "ComplexProject",
                    "cursor",
                    "Do a complex thing",
                    "myModel",
                    CredentialSource.PLATFORM
            );

            UUID prepId = UUID.randomUUID();
            UUID previewId = UUID.randomUUID();
            GenerationPreparationResponse prepResponse = new GenerationPreparationResponse(
                    prepId,
                    "ComplexProject",
                    "cursor",
                    null,
                    null,
                    false,
                    null,
                    new com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary(
                            com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity.COMPLEX,
                            java.util.List.of("multi-tenant"),
                            "saas | frontend + backend",
                            com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                            "elevated",
                            true,
                            true,
                            false,
                            "starter",
                            "google:gemini-3-pro",
                            com.arslanca.orchestrai.business.dtos.ai.ModelCapability.HIGH,
                            false,
                            "none",
                            com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary.CURRENT_CONTRACT_VERSION)
            );
            PlanningPreview preview = new PlanningPreview(
                    previewId,
                    prepId,
                    "nextjs-saas-shell",
                    com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.MULTI_PASS_EXPANSION,
                    PlanningPreviewStatus.REJECTED,
                    java.util.Map.of("qualityPassed", false),
                    java.util.Map.of("status", "rejected"),
                    PlanningPreview.CURRENT_CONTRACT_VERSION,
                    null);

            when(preparationService.prepareGeneration(eq(userId), any(GenerationPreparationRequest.class)))
                    .thenReturn(prepResponse);
            when(lifecycleAuthority.requiresAcceptedPreview(prepResponse)).thenReturn(true);
            when(planningPreviewService.generatePreview(eq(userId), any(GenerationPreviewRequest.class)))
                    .thenReturn(preview);
            when(lifecycleAuthority.previewRequiresAcceptance(preview))
                    .thenThrow(new PreparationStateException("Generated planning preview did not pass quality gates and cannot be auto-accepted."));

            assertThatThrownBy(() -> projectGenerationManager.generateBlueprint(userId, request, publisher))
                    .isInstanceOf(PreparationStateException.class)
                    .hasMessageContaining("did not pass quality gates");

            verify(planningPreviewService, never()).acceptPreview(any(), any());
            verifyNoInteractions(preparedGenerationService);
        }

        @Test
        void shouldPropagateExceptionWhenPreparationFails() {
            // Arrange
            ProjectRequest request = new ProjectRequest(
                    "TestProject",
                    "cursor",
                    "Do a thing",
                    "myModel",
                    CredentialSource.PLATFORM
            );

            RuntimeException prepException = new RuntimeException("Preparation failed");
            when(preparationService.prepareGeneration(eq(userId), any(GenerationPreparationRequest.class)))
                    .thenThrow(prepException);

            // Act & Assert
            assertThatThrownBy(() -> projectGenerationManager.generateBlueprint(userId, request, publisher))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Preparation failed");

            verifyNoInteractions(preparedGenerationService);
        }
    }
}
