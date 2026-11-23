package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.core.exception.type.SecretEncryptionException;
import com.arslanca.orchestrai.core.security.EncryptedSecret;
import com.arslanca.orchestrai.core.security.SecretEncryptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class GitHubAiOAuthCompletionTokenService {

    private final SecretEncryptionService secretEncryptionService;
    private final ObjectMapper objectMapper;

    public GitHubAiOAuthCompletionTokenService(
            final SecretEncryptionService secretEncryptionService,
            final ObjectMapper objectMapper) {
        this.secretEncryptionService = secretEncryptionService;
        this.objectMapper = objectMapper;
    }

    public String issue(final GitHubAiOAuthCompletionPayload payload) {
        try {
            final String serializedPayload = objectMapper.writeValueAsString(payload);
            final EncryptedSecret encryptedSecret = secretEncryptionService.encrypt(serializedPayload);
            return encryptedSecret.keyVersion() + "." + encryptedSecret.payload();
        } catch (final JsonProcessingException ex) {
            throw new SecretEncryptionException("OAuth completion payload serilestirilemedi.", ex);
        }
    }

    public GitHubAiOAuthCompletionPayload decode(final String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Completion token bos olamaz.");
        }

        final int separatorIndex = token.indexOf('.');
        if (separatorIndex <= 0 || separatorIndex == token.length() - 1) {
            throw new IllegalArgumentException("Completion token formati gecersiz.");
        }

        try {
            final int keyVersion = Integer.parseInt(token.substring(0, separatorIndex));
            final String payload = token.substring(separatorIndex + 1);
            final String decryptedPayload = secretEncryptionService.decrypt(new EncryptedSecret(payload, keyVersion));
            final GitHubAiOAuthCompletionPayload completionPayload = objectMapper.readValue(
                    decryptedPayload,
                    GitHubAiOAuthCompletionPayload.class);
            final OffsetDateTime completionExpiresAt = OffsetDateTime.parse(completionPayload.completionExpiresAt());
            if (completionExpiresAt.isBefore(OffsetDateTime.now())) {
                throw new IllegalArgumentException("Completion token suresi dolmus.");
            }
            return completionPayload;
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("Completion token key version gecersiz.");
        } catch (final SecretEncryptionException ex) {
            throw new IllegalArgumentException("Completion token cozumlenemedi.");
        } catch (final JsonProcessingException ex) {
            throw new IllegalArgumentException("Completion token payload cozumlenemedi.");
        }
    }
}
