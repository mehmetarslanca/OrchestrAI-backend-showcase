package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentDocumentComposer {

    public List<ComposedDocument> compose(final BlueprintPlan plan) {
        final List<ComposedDocument> documents = new ArrayList<>();
        if (plan.agents() == null) {
            return documents;
        }

        for (var agent : plan.agents()) {
            final AgentDeliveryContext context = AgentDeliveryContextFactory.resolve(plan, agent);
            final List<String> sections = new ArrayList<>();
            final StringBuilder doc = new StringBuilder();
            doc.append("# ").append(agent.role()).append("\n\n");

            appendTextSection(doc, sections, "Mission", "mission", context.mission());
            appendTextSection(doc, sections, "When To Use This Agent", "when-to-use", context.whenToUse());
            appendBulletSection(doc, sections, "Allowed Scope", "allowed-scope", context.allowedScope());
            appendBulletSection(doc, sections, "Forbidden Scope", "forbidden-scope", context.forbiddenScope());
            appendBulletSection(doc, sections, "Required Inputs", "required-inputs", context.requiredInputs());
            appendBulletSection(doc, sections, "Expected Outputs", "expected-outputs", context.expectedOutputs());
            appendBulletSection(doc, sections, "Quality Checklist", "quality-checklist", context.qualityChecklist());
            appendBulletSection(doc, sections, "Handoff Rules", "handoff-rules", context.handoffRules());

            if (!agent.systemPrompt().isBlank()) {
                sections.add("system-prompt");
                doc.append("## System Prompt\n\n");
                doc.append(agent.systemPrompt()).append("\n\n");
            }
            if (!agent.responsibilities().isEmpty()) {
                sections.add("responsibilities");
                doc.append("## Responsibilities\n\n");
                agent.responsibilities().forEach(responsibility -> doc.append("- ").append(responsibility).append("\n"));
                doc.append("\n");
            }

            final String id = DeliveryDocumentIdFactory.agentId(agent.role());
            documents.add(new ComposedDocument(DocumentKind.AGENT, id, agent.role(), sections, doc.toString()));
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
