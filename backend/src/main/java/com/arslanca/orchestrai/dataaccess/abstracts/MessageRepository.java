package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByChatIdOrderByCreatedAtAsc(UUID chatId);

    /**
     * Chat'teki mesaj sayısını döner (context window limit kontrolü için).
     */
    long countByChatId(UUID chatId);

    /**
     * Chat'teki son N mesajı getirir (LLM context window için).
     * En eski mesajdan en yeniye sıralı.
     */
    @Query(value = "SELECT m FROM MessageEntity m WHERE m.chat.id = :chatId ORDER BY m.createdAt DESC LIMIT :limit")
    List<MessageEntity> findRecentMessagesByChatId(@Param("chatId") UUID chatId, @Param("limit") int limit);

    /**
     * Gets unsummarized messages efficiently skipping the already summarized ones.
     */
    @Query(value = "SELECT * FROM messages WHERE chat_id = :chatId ORDER BY created_at ASC OFFSET :offset", nativeQuery = true)
    List<MessageEntity> findUnsummarizedMessagesByChatId(@Param("chatId") UUID chatId, @Param("offset") long offset);
}
