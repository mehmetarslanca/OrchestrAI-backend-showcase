package com.arslanca.orchestrai.business.delivery.layout;

import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record IdeRenderedLayout(
        List<FileNode> files,
        Map<String, String> renderedDocumentPaths) {

    public IdeRenderedLayout {
        files = files == null ? List.of() : List.copyOf(files);
        renderedDocumentPaths = renderedDocumentPaths == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(renderedDocumentPaths));
    }
}
