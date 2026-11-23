package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.CreditTransactionService;
import com.arslanca.orchestrai.business.dtos.credit.CreditTransactionResponse;
import com.arslanca.orchestrai.dataaccess.abstracts.CreditTransactionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.CreditTransactionEntity;
import com.arslanca.orchestrai.entity.enums.TransactionStatus;
import com.arslanca.orchestrai.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class CreditTransactionManager implements CreditTransactionService {
  private final CreditTransactionRepository repository;
  private final UserRepository userRepository;

    public CreditTransactionManager(CreditTransactionRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }


  @Override
  @Transactional(readOnly = true)
  public Page<CreditTransactionResponse> getTransactionHistory(UUID userId, Pageable pageable) {
    return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(entity -> new CreditTransactionResponse(
            entity.getId(), entity.getType().name(), entity.getAmount(),
            entity.getDescription(), entity.getReferenceId(),
            entity.getStatus().name(), entity.getCreatedAt()));
  }

  @Override
  @Transactional
  public void recordUsage(UUID userId, String projectName) {
    var user = userRepository.getReferenceById(userId);
    var tx = CreditTransactionEntity.builder()
        .user(user).type(TransactionType.USAGE).amount(1)
        .description("Project generated: " + projectName)
        .status(TransactionStatus.COMPLETED).build();
    repository.save(tx);
  }

  @Override
  @Transactional
  public void recordProjectGenerationReservation(UUID userId, String projectName, String referenceId) {
    if (repository.findByReferenceIdAndType(referenceId, TransactionType.USAGE).isPresent()) {
      return;
    }

    var user = userRepository.getReferenceById(userId);
    var tx = CreditTransactionEntity.builder()
        .user(user)
        .type(TransactionType.USAGE)
        .amount(1)
        .description("Project generation reserved: " + projectName)
        .referenceId(referenceId)
        .status(TransactionStatus.PENDING)
        .build();
    repository.save(tx);
  }

  @Override
  @Transactional
  public void captureProjectGenerationReservation(String referenceId) {
    final CreditTransactionEntity transaction = repository.findByReferenceIdAndTypeForUpdate(referenceId, TransactionType.USAGE)
        .orElseThrow(() -> new IllegalStateException("Usage reservation not found: " + referenceId));

    if (transaction.isCompleted()) {
      return;
    }
    if (transaction.isFailed()) {
      throw new IllegalStateException("Usage reservation already failed: " + referenceId);
    }

    transaction.markCompleted();
    repository.save(transaction);
  }

  @Override
  @Transactional
  public void failProjectGenerationReservation(String referenceId, String failureReason) {
    final Optional<CreditTransactionEntity> transactionOptional = repository.findByReferenceIdAndTypeForUpdate(referenceId, TransactionType.USAGE);
    if (transactionOptional.isEmpty()) {
      return;
    }

    final CreditTransactionEntity transaction = transactionOptional.get();
    if (transaction.isCompleted() || transaction.isFailed()) {
      return;
    }

    transaction.markFailed(failureReason);
    repository.save(transaction);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TransactionStatus> getProjectGenerationReservationStatus(String referenceId) {
    if (referenceId == null || referenceId.isBlank()) {
      return Optional.empty();
    }
    return repository.findByReferenceIdAndType(referenceId, TransactionType.USAGE)
        .map(CreditTransactionEntity::getStatus);
  }

  @Override
  @Transactional
  public void recordRefund(UUID userId, String projectName) {
    recordRefund(userId, projectName, null);
  }

  @Override
  @Transactional
  public void recordRefund(UUID userId, String projectName, String referenceId) {
    if (referenceId != null && repository.existsByReferenceIdAndType(referenceId, TransactionType.REFUND)) {
        return; // exactly-once refund semantic
    }
    var user = userRepository.getReferenceById(userId);
    var tx = CreditTransactionEntity.builder()
        .user(user).type(TransactionType.REFUND).amount(1)
        .description("Refund for project: " + projectName)
        .referenceId(referenceId)
        .status(TransactionStatus.COMPLETED).build();
    repository.save(tx);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasRefunded(String referenceId) {
      if (referenceId == null) return false;
      return repository.existsByReferenceIdAndType(referenceId, TransactionType.REFUND);
  }

  @Override
  @Transactional
  public void recordPurchase(UUID userId, int amount, String stripeSessionId) {
    var user = userRepository.getReferenceById(userId);
    var tx = CreditTransactionEntity.builder()
        .user(user).type(TransactionType.PURCHASE).amount(amount)
        .description("Purchased " + amount + " credits")
        .referenceId(stripeSessionId)
        .status(TransactionStatus.COMPLETED).build();
    repository.save(tx);
  }
}
