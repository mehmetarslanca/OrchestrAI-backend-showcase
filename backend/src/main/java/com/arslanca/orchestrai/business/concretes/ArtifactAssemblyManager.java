package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.ArtifactAssemblyService;
import com.arslanca.orchestrai.business.abstracts.delivery.BlueprintDeliveryService;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.entity.enums.IdeTarget;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class ArtifactAssemblyManager implements ArtifactAssemblyService {

  private static final Logger log = LoggerFactory.getLogger(ArtifactAssemblyManager.class);

  private final BlueprintDeliveryService blueprintDeliveryService;
  private final ArtifactSecurityValidator securityValidator;
  private final com.arslanca.orchestrai.business.delivery.policy.ReservedPathPolicy reservedPathPolicy;

  @Override
  public DeliveryResult assembleArtifact(BlueprintPlan blueprint, MaterializedStarter starter, IdeTarget ideTarget) {
    log.info("Assembling artifact for project={}, ideTarget={}", blueprint.projectName(), ideTarget);

    // 1. Get base IDE tree (system owned)
    DeliveryResult systemDelivery = blueprintDeliveryService.deliver(blueprint, ideTarget);
    List<FileNode> systemFiles = new ArrayList<>();

    for (FileNode node : systemDelivery.tree()) {
      String path = normalizePath(node.path());
      validatePath(path);
      systemFiles.add(new FileNode(path, node.content(), node.contentEncoding(), node.sizeBytes(), node.binary()));
    }

    Set<String> systemPaths = systemFiles.stream()
        .map(FileNode::path)
        .map(this::comparisonKey)
        .collect(Collectors.toSet());

    // 2. Process starter tree
    List<FileNode> mergedFiles = new ArrayList<>();

    // Add starter files first (if any)
    if (starter != null && starter.tree() != null) {
      for (var node : starter.tree()) {
        String path = normalizePath(node.path());
        validatePath(path);

        if (!securityValidator.isSafeMaterializedStarterNode(path, node.binary())) {
          log.debug("Stripping unsafe starter node from artifact: {}", path);
          continue;
        }

        // Enforce reserved path collision
        if (reservedPathPolicy.isReserved(path) || systemPaths.contains(comparisonKey(path))) {
          throw new IllegalStateException("Starter collision with reserved path: " + path);
        }

        mergedFiles.add(new FileNode(path, node.content(), node.contentEncoding(), node.sizeBytes(), node.binary()));
      }
    }

    // Add system files (override anything, but we already fail-loud on collision)
    mergedFiles.addAll(systemFiles);

    securityValidator.validateFiles(mergedFiles);

    return new DeliveryResult(
        systemDelivery.projectName(),
        systemDelivery.ideTarget(),
        mergedFiles,
        systemDelivery.manifest());
  }

  private String normalizePath(String path) {
    if (path == null)
      return "";
    String normalized = path.replace("\\", "/").replaceAll("/+", "/").trim();
    while (normalized.startsWith("./")) {
      normalized = normalized.substring(2);
    }
    return normalized;
  }

  private void validatePath(String path) {
    if (path == null || path.trim().isEmpty()) {
      throw new IllegalArgumentException("Path cannot be empty or blank");
    }
    if (path.startsWith("/")) {
      throw new IllegalArgumentException("Absolute paths are not allowed: " + path);
    }
    if (path.matches("^[a-zA-Z]:.*")) {
      throw new IllegalArgumentException("Windows absolute paths are not allowed: " + path);
    }
    if (path.contains("..") || path.equals(".") || path.contains("/./") || path.endsWith("/.")) {
      throw new IllegalArgumentException("Directory traversal/dot-segments are not allowed: " + path);
    }
  }

  private String comparisonKey(String path) {
    return path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
  }
}
