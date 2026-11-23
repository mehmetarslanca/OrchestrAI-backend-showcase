package com.arslanca.orchestrai.business.dtos.credit;
import java.time.OffsetDateTime;
import java.util.UUID;
public record CreditTransactionResponse(
    UUID id, String type, int amount, String description,
    String referenceId, String status, OffsetDateTime createdAt
) {}
