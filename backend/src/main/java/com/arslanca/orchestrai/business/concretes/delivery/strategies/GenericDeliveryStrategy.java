package com.arslanca.orchestrai.business.concretes.delivery.strategies;

import com.arslanca.orchestrai.business.abstracts.delivery.IdeDeliveryStrategy;
import com.arslanca.orchestrai.business.delivery.composition.ComposedDocument;
import com.arslanca.orchestrai.business.delivery.composition.DeliveryComposition;
import com.arslanca.orchestrai.business.delivery.composition.DocumentKind;
import com.arslanca.orchestrai.business.delivery.layout.IdeRenderedLayout;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenericDeliveryStrategy implements IdeDeliveryStrategy {

    @Override
    public String supportedIde() {
        return "generic";
    }

    @Override
    public IdeRenderedLayout transform(final DeliveryComposition composition) {
        final List<FileNode> files = new ArrayList<>();
        final Map<String, String> renderedPaths = new LinkedHashMap<>();

        for (ComposedDocument doc : composition.documents()) {
            final String path;
            if (doc.kind() == DocumentKind.GLOBAL_RULES) {
                path = "docs/rules.md";
            } else if (doc.kind() == DocumentKind.ARCHITECTURE_OVERVIEW) {
                path = "docs/architect.md";
            } else if (doc.kind() == DocumentKind.PHASE) {
                path = "docs/phases/" + doc.id() + ".md";
            } else if (doc.kind() == DocumentKind.AGENT) {
                path = "docs/agents/" + doc.id() + ".md";
            } else {
                continue;
            }
            files.add(new FileNode(path, doc.content()));
            renderedPaths.put(doc.id(), path);
        }

        return new IdeRenderedLayout(files, renderedPaths);
    }
}
