package com.arslanca.orchestrai.core.security;

import com.arslanca.orchestrai.core.config.CredentialEncryptionProperties;
import com.arslanca.orchestrai.core.exception.type.SecretEncryptionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretEncryptionServiceTest {

    @Test
    void shouldEncryptAndDecryptSecret() {
        SecretEncryptionService service = new SecretEncryptionService(properties());

        EncryptedSecret encryptedSecret = service.encrypt("sk-test-123456");

        assertThat(encryptedSecret.payload()).isNotBlank();
        assertThat(service.decrypt(encryptedSecret)).isEqualTo("sk-test-123456");
    }

    @Test
    void shouldProduceDifferentCiphertextForSameSecret() {
        SecretEncryptionService service = new SecretEncryptionService(properties());

        EncryptedSecret first = service.encrypt("sk-test-123456");
        EncryptedSecret second = service.encrypt("sk-test-123456");

        assertThat(first.payload()).isNotEqualTo(second.payload());
    }

    @Test
    void shouldRejectWeakMasterKey() {
        assertThatThrownBy(() -> new SecretEncryptionService(new CredentialEncryptionProperties("too-short", 1)))
                .isInstanceOf(SecretEncryptionException.class)
                .hasMessageContaining("LLM_CREDENTIALS_MASTER_KEY");
    }

    @Test
    void shouldRejectUnsupportedKeyVersionDuringDecrypt() {
        SecretEncryptionService service = new SecretEncryptionService(properties());

        assertThatThrownBy(() -> service.decrypt(new EncryptedSecret("payload", 99)))
                .isInstanceOf(SecretEncryptionException.class)
                .hasMessageContaining("Desteklenmeyen secret key version");
    }

    private CredentialEncryptionProperties properties() {
        return new CredentialEncryptionProperties("01234567890123456789012345678901", 1);
    }
}
