package com.arslanca.orchestrai.core.security;

public record EncryptedSecret(
        String payload,
        int keyVersion) {
}
