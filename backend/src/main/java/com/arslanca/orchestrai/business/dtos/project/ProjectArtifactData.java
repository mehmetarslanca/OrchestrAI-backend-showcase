package com.arslanca.orchestrai.business.dtos.project;

import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectArtifactData(
        String version,
        List<FileNode> tree,
        DeliveryManifest manifest
) {

    public static final String CURRENT_VERSION = "v2";

    public ProjectArtifactData(
            final String version,
            final List<FileNode> tree) {
        this(version, tree, DeliveryManifest.empty());
    }

    public ProjectArtifactData {
        version = version == null || version.isBlank() ? CURRENT_VERSION : version.trim();
        tree = tree == null ? List.of() : List.copyOf(tree);
        manifest = manifest == null ? DeliveryManifest.empty() : manifest;
    }
}
