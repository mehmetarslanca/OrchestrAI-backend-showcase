package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.CreditTransactionService;
import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;
import com.arslanca.orchestrai.business.dtos.credit.CreditTransactionResponse;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credits")
public class CreditController {

    private final CreditManagementService creditManagementService;
    private final CreditTransactionService creditTransactionService;

    public CreditController(CreditManagementService creditManagementService, CreditTransactionService creditTransactionService) {
        this.creditManagementService = creditManagementService;
        this.creditTransactionService = creditTransactionService;
    }


    @GetMapping("/me")
    public ResponseEntity<CreditResponse> getMyCredits(
            @AuthenticationPrincipal final AuthenticatedUser user) {
        final CreditResponse response = creditManagementService.getCreditBalance(user.id());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<CreditTransactionResponse>> getTransactionHistory(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(creditTransactionService.getTransactionHistory(user.id(), pageable));
    }
}
