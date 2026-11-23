package com.arslanca.orchestrai.business.delivery.manifest;

import com.arslanca.orchestrai.business.delivery.composition.ComposedDocument;
import com.arslanca.orchestrai.business.delivery.composition.DocumentKind;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryManifestAssemblerTest {

    @Test
    void shouldAssembleManifestCorrectly() {
        DeliveryManifestAssembler assembler = new DeliveryManifestAssembler();
        BlueprintPlan blueprint = new BlueprintPlan(
                "Acme Platform",
                "Executive summary",
                "Architecture overview",
                "System context",
                List.of("Use typed contracts"),
                List.of(),
                List.of("availability"),
                List.of("security"),
                List.of("tenant"),
                List.of(),
                List.of(),
                "Global rules",
                "Starter summary");

        List<ComposedDocument> docs = List.of(
                new ComposedDocument(DocumentKind.GLOBAL_RULES, "rules", "Global Rules", List.of("rules"), "content of rules"),
                new ComposedDocument(DocumentKind.PHASE, "phase_1", "Phase 1", List.of("purpose", "deliverables"), "phase 1 content")
        );

        DeliveryManifest manifest = assembler.assemble(blueprint, docs);

        assertThat(manifest).isNotNull();
        assertThat(manifest.schemaVersion()).isEqualTo("2.0");
        assertThat(manifest.projectName()).isEqualTo("Acme Platform");
        assertThat(manifest.executiveSummary()).isEqualTo("Executive summary");
        assertThat(manifest.totalDocuments()).isEqualTo(2);
        assertThat(manifest.inventory()).hasSize(2);
        assertThat(manifest.inventory().get(0).id()).isEqualTo("rules");
        assertThat(manifest.inventory().get(0).title()).isEqualTo("Global Rules");
        assertThat(manifest.inventory().get(1).sections()).containsExactly("purpose", "deliverables");
        assertThat(manifest.inventory().get(0).contentLength()).isEqualTo(16);
        assertThat(manifest.inventory().get(0).renderedPath()).isBlank();
    }
}
