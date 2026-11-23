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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageTransactionServiceTest {

    @Mock private ChatRepository chatRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private CreditManagementService creditManagementService;

    @InjectMocks
    private ChatMessageTransactionService service;

    private UUID chatId;
    private UUID userId;
    private ChatEntity chatEntity;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
        userId = UUID.randomUUID();
        chatEntity = ChatEntity.builder()
                .id(chatId)
                .status(ChatStatus.IDLE)
                .build();
    }

    @Test
    @DisplayName("lockChatForGeneration: Should throw DuplicateRequestException if chat is already GENERATING")
    void lockChatForGeneration_WhenGenerating_ThrowsDuplicateRequestException() {
        chatEntity.markAsGenerating();
        when(chatRepository.findByIdAndUserIdForUpdate(chatId, userId)).thenReturn(Optional.of(chatEntity));

        assertThatThrownBy(() -> service.lockChatForGeneration(chatId, userId, "hello"))
                .isInstanceOf(DuplicateRequestException.class)
                .hasMessageContaining("already an active AI generation");

        verify(messageRepository, never()).save(any());
        verify(chatRepository, never()).save(any());
    }

    @Test
    @DisplayName("lockChatForGeneration: Should lock chat, set to GENERATING, and save user message")
    void lockChatForGeneration_WhenIdle_LocksAndSavesMessage() {
        when(chatRepository.findByIdAndUserIdForUpdate(chatId, userId)).thenReturn(Optional.of(chatEntity));

        ChatEntity result = service.lockChatForGeneration(chatId, userId, "Test message");

        assertThat(result.getStatus()).isEqualTo(ChatStatus.GENERATING);
        verify(chatRepository).save(chatEntity);

        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageRepository).save(messageCaptor.capture());

        MessageEntity savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getChat()).isEqualTo(chatEntity);
        assertThat(savedMessage.getRole()).isEqualTo(MessageRole.USER);
        assertThat(savedMessage.getContent()).isEqualTo("Test message");
    }

    @Test
    @DisplayName("lockChatForGeneration: Should throw ResourceNotFoundException if chat not found")
    void lockChatForGeneration_WhenChatNotFound_ThrowsException() {
        when(chatRepository.findByIdAndUserIdForUpdate(chatId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lockChatForGeneration(chatId, userId, "Test message"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("saveAssistantMessageAndDeductCreditAndUnlock: Should save message, deduct credit, and set IDLE")
    void saveAssistantMessageAndDeductCreditAndUnlock_ShouldExecuteSuccessfully() {
        chatEntity.markAsGenerating();
        when(chatRepository.findByIdAndUserIdForUpdate(chatId, userId)).thenReturn(Optional.of(chatEntity));

        service.saveAssistantMessageAndUnlock(chatId, userId, "AI Response");

        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageRepository).save(messageCaptor.capture());

        MessageEntity savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(savedMessage.getContent()).isEqualTo("AI Response");

        assertThat(chatEntity.getStatus()).isEqualTo(ChatStatus.IDLE);
        verify(chatRepository).save(chatEntity);
    }

    @Test
    @DisplayName("unlockChatAndRefund: Should set to CANCELLED and not refund when shouldRefund is false")
    void unlockChatAndRefund_WhenNotRefund_SetsCancelled() {
        chatEntity.markAsGenerating();
        when(chatRepository.findByIdAndUserIdForUpdate(chatId, userId)).thenReturn(Optional.of(chatEntity));

        service.unlockChatAndRefund(chatId, userId, false);

        assertThat(chatEntity.getStatus()).isEqualTo(ChatStatus.CANCELLED);
        verify(chatRepository).save(chatEntity);
        verify(creditManagementService, never()).refundCredit(any());
    }

    @Test
    @DisplayName("unlockChatAndRefund: Should set to CANCELLED and refund when shouldRefund is true")
    void unlockChatAndRefund_WhenRefund_SetsCancelledAndRefunds() {
        chatEntity.markAsGenerating();
        when(chatRepository.findByIdAndUserIdForUpdate(chatId, userId)).thenReturn(Optional.of(chatEntity));

        service.unlockChatAndRefund(chatId, userId, true);

        assertThat(chatEntity.getStatus()).isEqualTo(ChatStatus.CANCELLED);
        verify(chatRepository).save(chatEntity);
        verify(creditManagementService).refundCredit(userId);
    }
}
