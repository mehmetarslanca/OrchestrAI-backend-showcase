package com.arslanca.orchestrai.entity.enums;

/**
 * Chat mesajlarının rolünü belirler.
 *
 * <p>
 * {@code USER} — kullanıcı tarafından gönderilen mesaj.
 * {@code ASSISTANT} — LLM tarafından üretilen yanıt.
 * </p>
 */
public enum MessageRole {
    USER,
    ASSISTANT
}
