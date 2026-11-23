package com.arslanca.orchestrai.business.concretes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class ChatCancelListener implements MessageListener {

    private final ConversationManager conversationManager;
    private final DistributedChatStreamRegistry chatStreamRegistry;

    public ChatCancelListener(ConversationManager conversationManager, DistributedChatStreamRegistry chatStreamRegistry) {
        this.conversationManager = conversationManager;
        this.chatStreamRegistry = chatStreamRegistry;
    }


    @Override
    public void onMessage(final Message message, final byte[] pattern) {
        final UUID chatId = DistributedChatStreamRegistry.parseChatId(message.getBody());
        log.info("Received cross-node cancel signal for chat {}", chatId);
        try {
            if (chatStreamRegistry.isOwnedLocally(chatId) && chatStreamRegistry.isCancelRequested(chatId)) {
                conversationManager.internalStopStreamWithoutUserId(chatId);
            }
        } catch (Exception e) {
            log.error("Failed to process cancel signal: {}", e.getMessage());
        }
    }
}
