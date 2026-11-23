package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifest;
import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifestAssembler;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BlueprintDeliveryComposer {

  private final ArchitectDocumentComposer architectComposer;
  private final PhaseDocumentComposer phaseComposer;
  private final AgentDocumentComposer agentComposer;
  private final DeliveryManifestAssembler manifestAssembler;

  public BlueprintDeliveryComposer(
      final ArchitectDocumentComposer architectComposer,
      final PhaseDocumentComposer phaseComposer,
      final AgentDocumentComposer agentComposer,
      final DeliveryManifestAssembler manifestAssembler) {
    this.architectComposer = architectComposer;
    this.phaseComposer = phaseComposer;
    this.agentComposer = agentComposer;
    this.manifestAssembler = manifestAssembler;
  }

  public DeliveryComposition compose(final BlueprintPlan plan) {
    final List<ComposedDocument> documents = new ArrayList<>();

    if (plan.ideRules() != null && !plan.ideRules().isBlank()) {
      documents.add(new ComposedDocument(
          DocumentKind.GLOBAL_RULES,
          DeliveryDocumentIdFactory.globalRulesId(),
          "Global Rules",
          List.of("rules"),
          plan.ideRules()));
    }

    documents.add(architectComposer.compose(plan));
    documents.addAll(phaseComposer.compose(plan));
    documents.addAll(agentComposer.compose(plan));

    final DeliveryManifest manifest = manifestAssembler.assemble(plan, documents);

    return new DeliveryComposition(plan.projectName(), documents, manifest);
  }
}
