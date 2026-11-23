package com.arslanca.orchestrai.business.concretes.delivery;

import com.arslanca.orchestrai.business.abstracts.delivery.BlueprintDeliveryService;
import com.arslanca.orchestrai.business.abstracts.delivery.IdeDeliveryStrategy;
import com.arslanca.orchestrai.business.delivery.composition.BlueprintDeliveryComposer;
import com.arslanca.orchestrai.business.delivery.composition.DeliveryComposition;
import com.arslanca.orchestrai.business.delivery.layout.IdeRenderedLayout;
import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifest;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.entity.enums.IdeTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BlueprintDeliveryManager implements BlueprintDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(BlueprintDeliveryManager.class);

    private final IdeDeliveryStrategyFactory strategyFactory;
    private final BlueprintDeliveryComposer composer;

    public BlueprintDeliveryManager(IdeDeliveryStrategyFactory strategyFactory, BlueprintDeliveryComposer composer) {
        this.strategyFactory = strategyFactory;
        this.composer = composer;
    }


    @Override
    public DeliveryResult deliver(final BlueprintPlan blueprint, final IdeTarget ideTarget) {
        log.info("Starting blueprint delivery formatting: ide={}", ideTarget);

        final IdeDeliveryStrategy strategy = strategyFactory.getStrategy(ideTarget);
        log.info("Selected delivery strategy: {}", strategy.getClass().getSimpleName());

        final DeliveryComposition composition = composer.compose(blueprint);
        final IdeRenderedLayout renderedLayout = strategy.transform(composition);
        final List<FileNode> transformedTree = renderedLayout.files();
        final DeliveryManifest manifest = composition.manifest().withIdeTargetAndRenderedPaths(
                ideTarget.name().toLowerCase(),
                renderedLayout.renderedDocumentPaths());

        log.info("Blueprint delivery formatting completed: {} files generated", transformedTree.size());

        return new DeliveryResult(
                blueprint.projectName(),
                ideTarget.name().toLowerCase(),
                transformedTree,
                manifest);
    }
}
