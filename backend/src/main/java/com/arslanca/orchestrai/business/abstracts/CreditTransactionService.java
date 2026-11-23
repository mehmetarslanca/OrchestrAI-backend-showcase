package com.arslanca.orchestrai.business.abstracts;
import com.arslanca.orchestrai.business.dtos.credit.CreditTransactionResponse;
import com.arslanca.orchestrai.entity.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CreditTransactionService {
    Page<CreditTransactionResponse> getTransactionHistory(UUID userId, Pageable pageable);
    void recordUsage(UUID userId, String projectName);
    void recordProjectGenerationReservation(UUID userId, String projectName, String referenceId);
    void captureProjectGenerationReservation(String referenceId);
    void failProjectGenerationReservation(String referenceId, String failureReason);
    Optional<TransactionStatus> getProjectGenerationReservationStatus(String referenceId);
    void recordRefund(UUID userId, String projectName);
    void recordRefund(UUID userId, String projectName, String referenceId);
    boolean hasRefunded(String referenceId);
    void recordPurchase(UUID userId, int amount, String stripeSessionId);
}
