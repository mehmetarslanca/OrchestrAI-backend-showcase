package com.arslanca.orchestrai.business.planning.lifecycle;

import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationSourceChannel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

@ExtendWith(MockitoExtension.class)
class PlanningLifecycleAuthorityTest {

    @Mock
    private PreparationStateGuard preparationStateGuard;
    @Mock
    private ChatFreshnessPolicy chatFreshnessPolicy;
    @Mock
    private PreviewReusePolicy previewReusePolicy;
    @Mock
    private PreviewOwnershipValidator previewOwnershipValidator;
    @Mock
    private StartAuthorizationPolicy startAuthorizationPolicy;
    @Mock
    private LegacyBridgePolicy legacyBridgePolicy;

    private PlanningLifecycleAuthority authority;
    private ProjectGenerationPreparationEntity preparation;
    private ProjectPlanningPreviewEntity preview;

    @BeforeEach
    void setUp() {
        authority = new PlanningLifecycleAuthority(
                preparationStateGuard,
                chatFreshnessPolicy,
                previewReusePolicy,
                previewOwnershipValidator,
                startAuthorizationPolicy,
                legacyBridgePolicy);

        preparation = ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                GenerationSourceChannel.WEB.name(),
                "Acme Platform",
                "cursor",
                "Build an orchestration workspace",
                Map.of("projectName", "Acme Platform"),
                Map.of("recommendedStarter", Map.of("key", "nextjs-saas-shell")),
                "1.0",
                Map.of()
        );
        preview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                "some-cache-key",
                "1.0"
        );
    }

    @Test
    void ensurePreviewGenerationAllowed_DelegatesCorrectly() {
        authority.ensurePreviewGenerationAllowed(preparation);
        verify(preparationStateGuard).ensurePreviewGenerationAllowed(preparation);
        verify(chatFreshnessPolicy).ensurePreparationCurrent(preparation);
    }

    @Test
    void ensurePreviewAcceptanceAllowed_DelegatesCorrectly() {
        authority.ensurePreviewAcceptanceAllowed(preparation, preview);
        verify(preparationStateGuard).ensurePreviewAcceptanceAllowed(preparation, preview);
        verify(chatFreshnessPolicy).ensurePreparationCurrent(preparation);
    }

    @Test
    void ensureStartAllowed_DelegatesCorrectly() {
        authority.ensureStartAllowed(preparation);
        verify(preparationStateGuard).ensureStartAllowed(preparation);
        verify(chatFreshnessPolicy).ensurePreparationCurrent(preparation);
        verify(previewReusePolicy).ensureNoActiveDraftPreview(preparation.getId());
    }

    @Test
    void loadOwnedPreview_DelegatesCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID previewId = UUID.randomUUID();
        authority.loadOwnedPreview(userId, previewId);
        verify(previewOwnershipValidator).loadOwnedPreview(userId, previewId);
    }
}
