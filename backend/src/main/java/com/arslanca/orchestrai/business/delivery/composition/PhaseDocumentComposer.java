package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PhaseDocumentComposer {

    public List<ComposedDocument> compose(final BlueprintPlan plan) {
        final List<ComposedDocument> documents = new ArrayList<>();
        if (plan.phases() == null) {
            return documents;
        }

        for (var phase : plan.phases()) {
            final List<String> sections = new ArrayList<>();
            final StringBuilder doc = new StringBuilder();
            doc.append(String.format("# Phase %d: %s\n\n", phase.order(), phase.title()));

            final String purpose = phase.objective().isBlank() ? phase.description() : phase.objective();
            appendTextSection(doc, sections, "Purpose", "purpose", purpose);
            appendTextSection(doc, sections, "Why This Phase Exists", "why-this-phase-exists", phase.whyThisPhaseExists());
            appendBulletSection(doc, sections, "Scope In", "scope-in", phase.scopeIn());
            appendBulletSection(doc, sections, "Scope Out", "scope-out", phase.scopeOut());
            appendBulletSection(doc, sections, "Dependencies", "dependencies", phase.prerequisites());
            appendBulletSection(doc, sections, "Deliverables", "deliverables", phase.deliverables());
            appendBulletSection(doc, sections, "Architecture Decisions", "architecture-decisions",
                    phase.architectureDecisions());
            appendBulletSection(doc, sections, "Contracts", "contracts", phase.contracts());
            appendTextSection(doc, sections, "Data Impact", "data-impact", phase.dataImpact());
            appendBulletSection(doc, sections, "Security Considerations", "security-considerations",
                    phase.securityConsiderations());
            appendTextSection(doc, sections, "Observability Plan", "observability-plan", phase.observabilityPlan());
            appendTextSection(doc, sections, "Testing Strategy", "testing-strategy", phase.testingStrategy());
            appendBulletSection(doc, sections, "Acceptance Criteria", "acceptance-criteria", phase.acceptanceCriteria());
            appendBulletSection(doc, sections, "Risks", "risks", phase.risks());
            appendTextSection(doc, sections, "Handoff Instructions", "handoff-instructions", phase.handoffInstructions());
            appendBulletSection(doc, sections, "Constraints", "constraints", phase.constraints());

            final String id = DeliveryDocumentIdFactory.phaseId(phase.order(), phase.title());
            documents.add(new ComposedDocument(DocumentKind.PHASE, id, phase.title(), sections, doc.toString()));
        }
        return documents;
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
}
