package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.core.exception.type.DuplicateRequestException;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import com.arslanca.orchestrai.entity.concretes.MessageEntity;
import com.arslanca.orchestrai.entity.enums.ChatStatus;
import com.arslanca.orchestrai.entity.enums.MessageRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate;

import java.util.UUID;

@Service
@Slf4j
public class ChatMessageTransactionService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final CreditManagementService creditManagementService;

    public ChatMessageTransactionService(ChatRepository chatRepository, MessageRepository messageRepository, CreditManagementService creditManagementService) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.creditManagementService = creditManagementService;
    }


    @Transactional
    public ChatEntity lockChatForGeneration(UUID chatId, UUID userId, String userMessageContent) {
        log.debug("Attempting to lock chat {} for user {}", chatId, userId);
        
        ChatEntity chat = chatRepository.findByIdAndUserIdForUpdate(chatId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found or access denied"));

        if (chat.getStatus() == ChatStatus.GENERATING) {
            throw new DuplicateRequestException("There is already an active AI generation for this chat. Please wait or stop it.");
        }

        // Deduct credit upfront to avoid credit exhaustion vulnerability
        creditManagementService.deductCredit(userId);

        chat.markAsGenerating();
        chatRepository.save(chat);

        // Save user message immediately in this transaction
        MessageEntity userMessage = MessageEntity.builder()
                .chat(chat)
                .role(MessageRole.USER)
                .content(userMessageContent)
                .build();
        messageRepository.save(userMessage);

        // Initialize lazy project proxy so the caller (ConversationManager) can access it outside this transaction
        Hibernate.initialize(chat.getConversationProject());

        return chat;
    }

    @Transactional
    public void saveAssistantMessageAndUnlock(UUID chatId, UUID userId, String content) {
        log.debug("Saving assistant message and unlocking chat {}, user {}", chatId, userId);
        
        ChatEntity chat = chatRepository.findByIdAndUserIdForUpdate(chatId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat deleted or access denied"));

        // Save assistant message
        MessageEntity assistantMessage = MessageEntity.builder()
                .chat(chat)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .build();
        messageRepository.save(assistantMessage);

        // Unlock chat
        chat.markAsIdle();
        chatRepository.save(chat);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unlockChatAndRefund(UUID chatId, UUID userId, boolean shouldRefund) {
        log.debug("Unlocking chat {} for user {}, refund: {}", chatId, userId, shouldRefund);
        chatRepository.findByIdAndUserIdForUpdate(chatId, userId).ifPresent(chat -> {
            chat.markAsCancelled();
            chatRepository.save(chat);
            
            if (shouldRefund) {
                creditManagementService.refundCredit(userId);
            }
        });
    }


}
