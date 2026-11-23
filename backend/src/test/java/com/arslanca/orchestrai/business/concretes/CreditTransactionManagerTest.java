package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.arslanca.orchestrai.business.dtos.credit.CreditTransactionResponse;
import com.arslanca.orchestrai.dataaccess.abstracts.CreditTransactionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.CreditTransactionEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.TransactionStatus;
import com.arslanca.orchestrai.entity.enums.TransactionType;

@ExtendWith(MockitoExtension.class)
class CreditTransactionManagerTest {

    @Mock
    private CreditTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    private CreditTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        transactionManager = new CreditTransactionManager(transactionRepository, userRepository);
    }

    @Test
    @DisplayName("getTransactionHistory: should return mapped page")
    void shouldReturnTransactionHistory() {
        final UUID userId = UUID.randomUUID();
        final PageRequest pageRequest = PageRequest.of(0, 10);

        final CreditTransactionEntity entity = CreditTransactionEntity.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.USAGE)
                .amount(1)
                .description("desc")
                .referenceId("ref")
                .status(TransactionStatus.COMPLETED)
                .createdAt(OffsetDateTime.now())
                .build();

        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest))
                .thenReturn(new PageImpl<>(List.of(entity)));

        final Page<CreditTransactionResponse> page = transactionManager.getTransactionHistory(userId, pageRequest);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).type()).isEqualTo("USAGE");
        assertThat(page.getContent().get(0).amount()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordProjectGenerationReservation: should create pending usage transaction")
    void shouldRecordProjectGenerationReservation() {
        final UUID userId = UUID.randomUUID();
        final UserEntity user = UserEntity.builder().build();
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(transactionRepository.findByReferenceIdAndType("reservation-1", TransactionType.USAGE)).thenReturn(Optional.empty());

        transactionManager.recordProjectGenerationReservation(userId, "test-project", "reservation-1");

        verify(transactionRepository).save(argThat(tx -> tx.getType() == TransactionType.USAGE
                && tx.getStatus() == TransactionStatus.PENDING
                && "reservation-1".equals(tx.getReferenceId())));
    }

    @Test
    @DisplayName("captureProjectGenerationReservation: should mark pending usage as completed")
    void shouldCaptureReservedUsage() {
        final CreditTransactionEntity pending = CreditTransactionEntity.builder()
                .type(TransactionType.USAGE)
                .amount(1)
                .description("reserved")
                .referenceId("reservation-1")
                .status(TransactionStatus.PENDING)
                .build();
        when(transactionRepository.findByReferenceIdAndTypeForUpdate("reservation-1", TransactionType.USAGE))
                .thenReturn(Optional.of(pending));

        transactionManager.captureProjectGenerationReservation("reservation-1");

        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(transactionRepository).save(pending);
    }

    @Test
    @DisplayName("failProjectGenerationReservation: should mark pending usage as failed")
    void shouldFailReservedUsage() {
        final CreditTransactionEntity pending = CreditTransactionEntity.builder()
                .type(TransactionType.USAGE)
                .amount(1)
                .description("reserved")
                .referenceId("reservation-1")
                .status(TransactionStatus.PENDING)
                .build();
        when(transactionRepository.findByReferenceIdAndTypeForUpdate("reservation-1", TransactionType.USAGE))
                .thenReturn(Optional.of(pending));

        transactionManager.failProjectGenerationReservation("reservation-1", "PipelineFailure");

        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(pending.getDescription()).contains("PipelineFailure");
        verify(transactionRepository).save(pending);
    }
}
