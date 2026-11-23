package com.arslanca.orchestrai.core.security;

import com.arslanca.orchestrai.core.config.CredentialEncryptionProperties;
import com.arslanca.orchestrai.core.exception.type.SecretEncryptionException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class SecretEncryptionService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int MINIMUM_MASTER_KEY_LENGTH = 32;

    private final SecretKeySpec currentSecretKey;
    private final int currentKeyVersion;
    private final SecureRandom secureRandom;

    public SecretEncryptionService(final CredentialEncryptionProperties properties) {
        final String masterKey = properties.masterKey();
        if (masterKey == null || masterKey.isBlank() || masterKey.length() < MINIMUM_MASTER_KEY_LENGTH) {
            throw new SecretEncryptionException("LLM_CREDENTIALS_MASTER_KEY en az 32 karakter olmalidir.");
        }

        this.currentSecretKey = new SecretKeySpec(deriveKey(masterKey), "AES");
        this.currentKeyVersion = properties.currentKeyVersion();
        this.secureRandom = new SecureRandom();
    }

    public EncryptedSecret encrypt(final String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new SecretEncryptionException("Sifrelenecek secret bos olamaz.");
        }

        try {
            final byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, currentSecretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            final byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return new EncryptedSecret(Base64.getEncoder().encodeToString(combined), currentKeyVersion);
        } catch (final Exception ex) {
            throw new SecretEncryptionException("Secret sifrelenemedi.", ex);
        }
    }

    public String decrypt(final EncryptedSecret payload) {
        if (payload == null || payload.payload() == null || payload.payload().isBlank()) {
            throw new SecretEncryptionException("Cozulecek secret payload bos olamaz.");
        }
        if (payload.keyVersion() != currentKeyVersion) {
            throw new SecretEncryptionException("Desteklenmeyen secret key version: " + payload.keyVersion());
        }

        try {
            final byte[] combined = Base64.getDecoder().decode(payload.payload());
            if (combined.length <= GCM_IV_LENGTH_BYTES) {
                throw new SecretEncryptionException("Gecersiz encrypted secret payload.");
            }

            final byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            final byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, currentSecretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (final SecretEncryptionException ex) {
            throw ex;
        } catch (final Exception ex) {
            throw new SecretEncryptionException("Secret cozulurken hata olustu.", ex);
        }
    }

    public EncryptedSecret rotate(final EncryptedSecret payload) {
        return encrypt(decrypt(payload));
    }

    public int currentKeyVersion() {
        return currentKeyVersion;
    }

    private byte[] deriveKey(final String masterKey) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception ex) {
            throw new SecretEncryptionException("Master key turetilemedi.", ex);
        }
    }
}
