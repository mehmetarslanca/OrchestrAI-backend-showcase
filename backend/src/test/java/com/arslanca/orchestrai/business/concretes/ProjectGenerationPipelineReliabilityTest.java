package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.PreparedProjectGenerationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.ProjectPlanningPreviewService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.mapper.EntityMapper;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectArtifactRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectGenerationPipelineReliabilityTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectArtifactRepository projectArtifactRepository;

    @Mock
    private ProjectGenerationPreparationService preparationService;

    @Mock
    private PreparedProjectGenerationService preparedGenerationService;

    @Mock
    private EntityMapper entityMapper;

    @Mock
    private ProjectPlanningPreviewService planningPreviewService;

    @Mock
    private PlanningLifecycleAuthority lifecycleAuthority;

    private ProjectGenerationManager projectGenerationManager;

    @BeforeEach
    void setUp() {
        projectGenerationManager = new ProjectGenerationManager(
                projectRepository,
                projectArtifactRepository,
                entityMapper,
                preparationService,
                planningPreviewService,
                preparedGenerationService,
                lifecycleAuthority);
    }

    @Test
    void testGenerateBlueprint_ShouldPropagatePipelineException() {
        UUID userId = UUID.randomUUID();
        SseEventPublisher publisher = mock(SseEventPublisher.class);
        
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

        when(preparedGenerationService.startGenerationWithOverride(
                eq(userId),
                any(GenerationStartRequest.class),
                eq("myModel"),
                eq(CredentialSource.PLATFORM),
                eq(publisher)
        )).thenThrow(new InfrastructureUnavailableException("System overloaded"));

        assertThrows(InfrastructureUnavailableException.class, () -> 
                projectGenerationManager.generateBlueprint(userId, request, publisher));

        verify(preparationService, times(1)).prepareGeneration(eq(userId), any(GenerationPreparationRequest.class));
    }
}
