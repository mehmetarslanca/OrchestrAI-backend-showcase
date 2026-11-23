package com.arslanca.orchestrai.core.config.projectgeneration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "project-generation.security")
public class ArtifactSecurityProperties {
    private int maxFileCount = 1000;
    private long maxSingleFileBytes = 5_000_000; // 5MB
    private long maxTotalBytes = 25_000_000; // 25MB
    private List<String> forbiddenExtensions = List.of(".exe", ".dll", ".so", ".dylib", ".class", ".jar", ".war");
    private List<String> forbiddenSecretFiles = List.of(".env", "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519");

    public int getMaxFileCount() { return maxFileCount; }
    public void setMaxFileCount(int maxFileCount) { this.maxFileCount = maxFileCount; }

    public long getMaxSingleFileBytes() { return maxSingleFileBytes; }
    public void setMaxSingleFileBytes(long maxSingleFileBytes) { this.maxSingleFileBytes = maxSingleFileBytes; }

    public long getMaxTotalBytes() { return maxTotalBytes; }
    public void setMaxTotalBytes(long maxTotalBytes) { this.maxTotalBytes = maxTotalBytes; }

    public List<String> getForbiddenExtensions() { return forbiddenExtensions; }
    public void setForbiddenExtensions(List<String> forbiddenExtensions) { this.forbiddenExtensions = forbiddenExtensions; }

    public List<String> getForbiddenSecretFiles() { return forbiddenSecretFiles; }
    public void setForbiddenSecretFiles(List<String> forbiddenSecretFiles) { this.forbiddenSecretFiles = forbiddenSecretFiles; }
}
