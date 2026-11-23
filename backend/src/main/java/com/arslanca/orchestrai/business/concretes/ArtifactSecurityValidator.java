package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.core.config.projectgeneration.ArtifactSecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArtifactSecurityValidator {

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "(?is)-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----\\s*[A-Za-z0-9/+=\\r\\n]{64,}");
    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?im)\\b(api[_-]?key|secret[_-]?key|aws_access_key_id)\\b\\s*[:=]\\s*['\"]?([^\\s'\"\\r\\n]+)['\"]?");

    private final ArtifactSecurityProperties properties;
    private final MeterRegistry meterRegistry;

    public ArtifactSecurityValidator(ArtifactSecurityProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public void validateFiles(List<FileNode> files) {
        if (files.size() > properties.getMaxFileCount()) {
            reject("max_file_count", "Artifact file count exceeds maximum allowed limit: " + properties.getMaxFileCount());
        }

        long totalBytes = 0;
        for (FileNode file : files) {
            String path = file.path() != null ? file.path() : "";
            validatePath(path, file.binary());
            validateEncoding(file);

            long size = file.sizeBytes() > 0 ? file.sizeBytes() : byteCount(file.content());

            if (size > properties.getMaxSingleFileBytes()) {
                reject("max_single_file_size", "Single file size exceeds maximum allowed limit: " + path);
            }
            
            if (!file.binary()) {
                validateContent(path, file.content());
            }
            totalBytes += size;
        }

        if (totalBytes > properties.getMaxTotalBytes()) {
            reject("max_total_bytes", "Final artifact size exceeds maximum allowed limit: " + properties.getMaxTotalBytes());
        }
        
        meterRegistry.summary("project_generation.artifact.file_count").record(files.size());
        meterRegistry.summary("project_generation.artifact.total_bytes").record(totalBytes);
    }

    public boolean isSafeMaterializedStarterNode(final String path, final boolean binary) {
        try {
            validatePath(path, binary);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void validatePath(String path, boolean binary) {
        if (path == null || path.isBlank()) {
            reject("blank_path", "Artifact path cannot be blank");
        }

        final String normalizedPath = path.replace('\\', '/').replaceAll("/+", "/").trim();
        if (normalizedPath.startsWith("/")) {
            reject("absolute_path", "Absolute artifact paths are not allowed: " + path);
        }
        if (normalizedPath.matches("^[a-zA-Z]:.*")) {
            reject("absolute_path", "Windows absolute artifact paths are not allowed: " + path);
        }
        if (normalizedPath.equals(".")
                || normalizedPath.startsWith("./")
                || normalizedPath.contains("..")
                || normalizedPath.contains("/./")
                || normalizedPath.endsWith("/.")) {
            reject("path_traversal", "Artifact path contains invalid traversal segments: " + path);
        }

        String lowerPath = normalizedPath.toLowerCase();

        if (binary && expectsTextContent(lowerPath)) {
            reject("unexpected_binary_text_file", "Text-like artifact file cannot be marked binary: " + path);
        }

        // Trusted starter binaries are allowed through this phase; extension blocking remains text-only.
        if (!binary) {
            for (String ext : properties.getForbiddenExtensions()) {
                if (lowerPath.endsWith(ext)) {
                    reject("forbidden_extension", "Forbidden binary extension detected: " + path);
                }
            }
        }
        
        // Allowed templates
        if (lowerPath.endsWith(".env.example") || lowerPath.endsWith(".env.sample") || lowerPath.endsWith(".env.template")) {
            return;
        }

        // Secret files checking
        for (String forbidden : properties.getForbiddenSecretFiles()) {
            boolean isEnv = forbidden.equals(".env");
            
            boolean exactMatch = lowerPath.equals(forbidden) || lowerPath.endsWith("/" + forbidden);
            boolean extensionMatch = forbidden.startsWith(".") && lowerPath.endsWith(forbidden);
            boolean envPrefixMatch = isEnv && (lowerPath.startsWith(".env.") || lowerPath.contains("/.env."));
            
            if (exactMatch || extensionMatch || envPrefixMatch) {
                reject("secret_file", "Secret or credential file detected: " + path);
            }
        }
    }

    private void validateEncoding(final FileNode file) {
        final String encoding = file.contentEncoding() == null ? "" : file.contentEncoding().trim().toLowerCase();

        if (file.binary() && !FileNode.BASE64.equals(encoding)) {
            reject("invalid_binary_encoding", "Binary files must use base64 contentEncoding: " + file.path());
        }

        if (!file.binary() && !FileNode.UTF_8.equals(encoding)) {
            reject("invalid_text_encoding", "Text files must use utf-8 contentEncoding: " + file.path());
        }
    }

    private boolean expectsTextContent(final String lowerPath) {
        final String fileName = lowerPath.contains("/")
                ? lowerPath.substring(lowerPath.lastIndexOf('/') + 1)
                : lowerPath;

        return fileName.equals("dockerfile")
                || fileName.equals("package.json")
                || fileName.equals("pom.xml")
                || fileName.equals("readme")
                || fileName.equals("readme.md")
                || lowerPath.endsWith(".env")
                || lowerPath.endsWith(".example")
                || lowerPath.endsWith(".sample")
                || lowerPath.endsWith(".template")
                || lowerPath.endsWith(".properties")
                || lowerPath.endsWith(".yaml")
                || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".json")
                || lowerPath.endsWith(".md")
                || lowerPath.endsWith(".txt")
                || lowerPath.endsWith(".java")
                || lowerPath.endsWith(".kt")
                || lowerPath.endsWith(".xml")
                || lowerPath.endsWith(".sql")
                || lowerPath.endsWith(".sh")
                || lowerPath.endsWith(".bat")
                || lowerPath.endsWith(".gradle")
                || lowerPath.endsWith(".gitignore")
                || lowerPath.endsWith(".cursorrules")
                || lowerPath.endsWith(".js")
                || lowerPath.endsWith(".jsx")
                || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".tsx")
                || lowerPath.endsWith(".html")
                || lowerPath.endsWith(".css")
                || lowerPath.endsWith(".scss");
    }

    private void validateContent(String path, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        if (PRIVATE_KEY_PATTERN.matcher(content).find()) {
            reject("secret_content", "Secret content heuristics matched in file: " + path);
        }

        final Matcher matcher = SECRET_ASSIGNMENT_PATTERN.matcher(content);
        while (matcher.find()) {
            if (looksLikeRealSecretValue(matcher.group(2))) {
                reject("secret_content", "Secret content heuristics matched in file: " + path);
            }
        }
    }

    private boolean looksLikeRealSecretValue(final String rawValue) {
        if (rawValue == null) {
            return false;
        }

        final String value = rawValue.trim();
        if (value.length() < 10) {
            return false;
        }

        final String normalized = value.toUpperCase();
        return !normalized.contains("YOUR_")
                && !normalized.contains("DUMMY")
                && !normalized.startsWith("<")
                && !normalized.contains("...")
                && !normalized.contains("${");
    }

    private long byteCount(final String content) {
        return content == null ? 0L : content.getBytes(StandardCharsets.UTF_8).length;
    }

    private void reject(final String reason, final String message) {
        meterRegistry.counter("project_generation.security.rejection", "reason", reason).increment();
        throw new IllegalStateException(message);
    }
}
