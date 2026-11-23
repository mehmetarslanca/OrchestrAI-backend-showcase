package com.arslanca.orchestrai.business.concretes.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.arslanca.orchestrai.business.abstracts.delivery.IdeDeliveryStrategy;
import com.arslanca.orchestrai.business.delivery.composition.AgentDocumentComposer;
import com.arslanca.orchestrai.business.delivery.composition.ArchitectDocumentComposer;
import com.arslanca.orchestrai.business.delivery.composition.BlueprintDeliveryComposer;
import com.arslanca.orchestrai.business.delivery.composition.PhaseDocumentComposer;
import com.arslanca.orchestrai.business.delivery.layout.IdeRenderedLayout;
import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifestAssembler;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.entity.enums.IdeTarget;

class BlueprintDeliveryManagerTest {

    @Mock
    private IdeDeliveryStrategyFactory strategyFactory;

    @Mock
    private IdeDeliveryStrategy strategy;

    private BlueprintDeliveryManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new BlueprintDeliveryManager(strategyFactory, new BlueprintDeliveryComposer(
                new ArchitectDocumentComposer(),
                new PhaseDocumentComposer(),
                new AgentDocumentComposer(),
                new DeliveryManifestAssembler()));
    }

    @Test
    void shouldDeliverBlueprintUsingStrategy() {
        final BlueprintPlan blueprint = new BlueprintPlan(
                "MyProject",
                "Arch",
                List.of(),
                List.of(),
                "Rules",
                "starter notes");

        when(strategyFactory.getStrategy(IdeTarget.CURSOR)).thenReturn(strategy);
        when(strategy.transform(org.mockito.ArgumentMatchers.any())).thenReturn(new IdeRenderedLayout(
                List.of(new FileNode("test.md", "content")),
                java.util.Map.of("architect", "test.md")));

        final DeliveryResult result = manager.deliver(blueprint, IdeTarget.CURSOR);

        assertThat(result.projectName()).isEqualTo("MyProject");
        assertThat(result.ideTarget()).isEqualTo("cursor");
        assertThat(result.tree()).singleElement().extracting(FileNode::path).isEqualTo("test.md");
        assertThat(result.manifest().ideTarget()).isEqualTo("cursor");
    }
}
