package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.conversation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Konuşma sistemi servis arayüzü.
 *
 * <p>
 * Kullanıcı bazlı konuşma projeleri, chat session'ları ve mesaj yönetimi.
 * LLM ile chat bazlı SSE stream desteği içerir.
 * </p>
 */
public interface ConversationService {

    // ---- Proje CRUD ----

    /**
     * Kullanıcının tüm konuşma projelerini son güncelleme sırasına göre listeler.
     */
    List<ConversationProjectResponse> getUserProjects(UUID userId);

    /**
     * Yeni bir konuşma projesi oluşturur.
     */
    ConversationProjectResponse createProject(UUID userId, CreateProjectRequest request);

    /**
     * Konuşma projesini siler (CASCADE: tüm chat ve mesajlar da silinir).
     * Ownership doğrulaması yapılır.
     */
    void deleteProject(UUID userId, UUID projectId);

    // ---- Chat CRUD ----

    /**
     * Belirtilen projenin chat session'larını listeler.
     * Ownership doğrulaması yapılır.
     */
    List<ChatResponse> getProjectChats(UUID userId, UUID projectId);

    /**
     * Belirtilen proje altında yeni bir chat session'ı oluşturur.
     * Ownership doğrulaması yapılır.
     */
    ChatResponse createChat(UUID userId, UUID projectId, CreateChatRequest request);

    /**
     * Chat session'ını siler (CASCADE: tüm mesajlar da silinir).
     * Ownership doğrulaması yapılır.
     */
    void deleteChat(UUID userId, UUID chatId);

    // ---- Messages ----

    /**
     * Chat'teki tüm mesajları kronolojik sırayla listeler.
     * Ownership doğrulaması yapılır.
     */
    List<MessageResponse> getChatMessages(UUID userId, UUID chatId);

    /**
     * Chat'e mesaj gönderir ve LLM yanıtını SSE stream ile döner.
     *
     * <p>Akış:</p>
     * <ol>
     *   <li>User mesajını DB'ye kaydet</li>
     *   <li>Chat'in mesaj geçmişini context olarak al (son N mesaj)</li>
     *   <li>LLM'e gönder (Light model)</li>
     *   <li>SSE stream ile token-by-token yanıt aktar</li>
     *   <li>Stream tamamlanınca tam yanıtı DB'ye kaydet</li>
     *   <li>Kredi düş</li>
     * </ol>
     */
    SseEmitter sendMessageAndStream(UUID userId, UUID chatId, SendMessageRequest request);

    /**
     * Devam eden bir SSE stream'i durdurur.
     * Kullanıcı yeni mesaj göndermeden önce mevcut stream'in bitmesini beklemeli
     * veya açıkça durdurmalıdır.
     */
    void stopStream(UUID userId, UUID chatId);
}
