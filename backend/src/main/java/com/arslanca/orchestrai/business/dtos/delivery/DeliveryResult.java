package com.arslanca.orchestrai.business.dtos.delivery;

import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifest;

import java.util.List;

/**
 * The final payload returned to the CLI client.
 * Contains the project name, the target IDE, and the generated file tree.
 */
public record DeliveryResult(
        String projectName,
        String ideTarget,
        List<FileNode> tree,
        DeliveryManifest manifest) {

    public DeliveryResult(
            final String projectName,
            final String ideTarget,
            final List<FileNode> tree) {
        this(projectName, ideTarget, tree, DeliveryManifest.empty());
    }

    public DeliveryResult {
        projectName = projectName == null ? "" : projectName.trim();
        ideTarget = ideTarget == null ? "" : ideTarget.trim();
        tree = tree == null ? List.of() : List.copyOf(tree);
        manifest = manifest == null ? DeliveryManifest.empty() : manifest;
    }
}
