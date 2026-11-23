package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArchitectDocumentComposer {

    public ComposedDocument compose(final BlueprintPlan plan) {
        final List<String> sections = new ArrayList<>();
        final StringBuilder doc = new StringBuilder();
        doc.append("# ").append(plan.projectName()).append(" - System Architecture\n\n");

        appendTextSection(doc, sections, "Executive Summary", "executive-summary", plan.executiveSummary());
        appendTextSection(doc, sections, "Architecture Overview", "architecture-overview", plan.architectureOverview());
        appendTextSection(doc, sections, "System Context", "system-context", plan.systemContext());
        appendBulletSection(doc, sections, "Major Architecture Decisions", "major-architecture-decisions",
                plan.majorArchitectureDecisions());
        appendWorkstreamMap(doc, sections, plan);
        appendBulletSection(doc, sections, "Global Constraints", "global-constraints",
                plan.phases().stream().flatMap(phase -> phase.constraints().stream()).distinct().toList());
        appendBulletSection(doc, sections, "Non-Functional Requirements", "non-functional-requirements",
                plan.nonFunctionalRequirements());
        appendBulletSection(doc, sections, "Cross-Cutting Concerns", "cross-cutting-concerns",
                plan.crossCuttingConcerns());
        appendBulletSection(doc, sections, "Glossary", "glossary", plan.glossary());
        appendBulletSection(doc, sections, "Assumptions", "assumptions", plan.assumptions());
        appendBulletSection(doc, sections, "Unknowns", "unknowns", plan.unknowns());
        appendTextSection(doc, sections, "Starter Notes", "starter-notes", plan.starterNotes());
        appendDeliveryWarnings(doc, sections, plan);

        if (!plan.phases().isEmpty()) {
            sections.add("development-phases");
            doc.append("## Development Phases\n\n");
            for (var phase : plan.phases()) {
                doc.append(String.format("### Phase %d: %s\n", phase.order(), phase.title()));
                doc.append(phase.description()).append("\n\n");
            }
        }

        return new ComposedDocument(
                DocumentKind.ARCHITECTURE_OVERVIEW,
                DeliveryDocumentIdFactory.architectId(),
                "System Architecture",
                sections,
                doc.toString());
    }

    private void appendTextSection(
            final StringBuilder document,
            final List<String> sections,
            final String heading,
            final String sectionKey,
            final String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sections.add(sectionKey);
        document.append("## ").append(heading).append("\n\n");
        document.append(value.trim()).append("\n\n");
    }

    private void appendBulletSection(
            final StringBuilder document,
            final List<String> sections,
            final String heading,
            final String sectionKey,
            final List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sections.add(sectionKey);
        document.append("## ").append(heading).append("\n\n");
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> document.append("- ").append(value.trim()).append("\n"));
        document.append("\n");
    }

    private void appendWorkstreamMap(
            final StringBuilder document,
            final List<String> sections,
            final BlueprintPlan plan) {
        if (plan.workstreams() == null || plan.workstreams().isEmpty()) {
            return;
        }

        sections.add("workstream-map");
        document.append("## Workstream Map\n\n");
        for (final BlueprintPlan.WorkstreamDefinition workstream : plan.workstreams()) {
            document.append("### ").append(workstream.title()).append("\n\n");
            if (!workstream.goal().isBlank()) {
                document.append("Goal: ").append(workstream.goal()).append("\n\n");
            }
            if (!workstream.whyItExists().isBlank()) {
                document.append("Why this workstream exists: ").append(workstream.whyItExists()).append("\n\n");
            }
            appendInlineBulletBlock(document, "Scope In", workstream.includedScope());
            appendInlineBulletBlock(document, "Scope Out", workstream.excludedScope());
            appendInlineBulletBlock(document, "Dependencies", workstream.dependencies());
            appendInlineBulletBlock(document, "Suggested Agents", workstream.suggestedAgents());
        }
    }

    private void appendDeliveryWarnings(
            final StringBuilder document,
            final List<String> sections,
            final BlueprintPlan plan) {
        final List<String> warnings = new ArrayList<>();
        plan.assumptions().forEach(value -> warnings.add("Validate assumption: " + value));
        plan.unknowns().forEach(value -> warnings.add("Resolve unknown: " + value));
        if (plan.starterNotes() != null && plan.starterNotes().toLowerCase().contains("docs-only")) {
            warnings.add("Starter resolution is operating in docs-only mode.");
        }

        appendBulletSection(document, sections, "Delivery Warnings", "delivery-warnings", warnings);
    }

    private void appendInlineBulletBlock(
            final StringBuilder document,
            final String heading,
            final List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        document.append("#### ").append(heading).append("\n\n");
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> document.append("- ").append(value.trim()).append("\n"));
        document.append("\n");
    }
}
