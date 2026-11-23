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
public class AntigravityDeliveryStrategy implements IdeDeliveryStrategy {

    @Override
    public String supportedIde() {
        return "antigravity"; // also covers windsurf conceptually via Factory mapping
    }

    @Override
    public IdeRenderedLayout transform(final DeliveryComposition composition) {
        final List<FileNode> files = new ArrayList<>();
        final Map<String, String> renderedPaths = new LinkedHashMap<>();

        for (ComposedDocument doc : composition.documents()) {
            final String path;
            final String content;
            if (doc.kind() == DocumentKind.GLOBAL_RULES) {
                path = ".agents/rules/rules.md";
                content = buildRulesDocument(composition.projectName(), doc.content());
            } else if (doc.kind() == DocumentKind.ARCHITECTURE_OVERVIEW) {
                path = "docs/architect.md";
                content = doc.content();
            } else if (doc.kind() == DocumentKind.PHASE) {
                path = "docs/phases/" + doc.id() + ".md";
                content = doc.content();
            } else if (doc.kind() == DocumentKind.AGENT) {
                path = ".agents/workflows/" + doc.id() + ".md";
                content = buildAgentWorkflow(doc);
            } else {
                continue;
            }
            files.add(new FileNode(path, content));
            renderedPaths.put(doc.id(), path);
        }

        return new IdeRenderedLayout(files, renderedPaths);
    }

    private String buildRulesDocument(String projectName, String rules) {
        final var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("trigger: always_on\n");
        sb.append("---\n\n");
        sb.append("# ").append(projectName).append(": Master Development Rules\n\n");
        sb.append(rules);
        return sb.toString();
    }

    private String buildAgentWorkflow(final ComposedDocument document) {
        final var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("description: ").append(document.title()).append("\n");
        sb.append("---\n\n");
        sb.append(document.content());
        return sb.toString();
    }
}
