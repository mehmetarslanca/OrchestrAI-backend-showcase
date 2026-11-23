package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.PreparedProjectGenerationService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationRequest;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectArtifactRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectGenerationManagerCancellationTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectArtifactRepository projectArtifactRepository;

    @Mock
    private ProjectGenerationPreparationService preparationService;

    @Mock
    private PreparedProjectGenerationService preparedGenerationService;

    @InjectMocks
    private ProjectGenerationManager projectGenerationManager;

    @Test
    void testGenerateBlueprint_ShouldAbortIfPreparationFails() {
        UUID userId = UUID.randomUUID();
        SseEventPublisher publisher = mock(SseEventPublisher.class);
        
        ProjectRequest request = new ProjectRequest(
                "TestProject",
                "cursor",
                "Do a thing",
                "myModel",
                CredentialSource.PLATFORM
        );

        when(preparationService.prepareGeneration(eq(userId), any(GenerationPreparationRequest.class)))
                .thenThrow(new RuntimeException("Preparation aborted"));

        assertThrows(RuntimeException.class, () -> 
                projectGenerationManager.generateBlueprint(userId, request, publisher));

        verify(preparedGenerationService, never()).startGenerationWithOverride(
                any(), any(), any(), any(), any()
        );
    }
}
