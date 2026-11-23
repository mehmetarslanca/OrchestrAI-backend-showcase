package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiUsageLedgerService;
import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.DashboardService;
import com.arslanca.orchestrai.business.abstracts.UserManagementService;
import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;
import com.arslanca.orchestrai.business.dtos.dashboard.DashboardStatsResponse;
import com.arslanca.orchestrai.business.dtos.user.UserResponse;
import com.arslanca.orchestrai.business.usage.AiUsageSpendSummary;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class DashboardManager implements DashboardService {

    private final CreditManagementService creditManagementService;
    private final ProjectRepository projectRepository;
    private final UserManagementService userManagementService;
    private final AiUsageLedgerService aiUsageLedgerService;

    public DashboardManager(
            final CreditManagementService creditManagementService,
            final ProjectRepository projectRepository,
            final UserManagementService userManagementService,
            final AiUsageLedgerService aiUsageLedgerService) {
        this.creditManagementService = creditManagementService;
        this.projectRepository = projectRepository;
        this.userManagementService = userManagementService;
        this.aiUsageLedgerService = aiUsageLedgerService;
    }


    @Override
    public DashboardStatsResponse getDashboardStats(final UUID userId) {
        final CreditResponse credits = creditManagementService.getCreditBalance(userId);
        final UserResponse user = userManagementService.getCurrentUser(userId);
        final OffsetDateTime windowEnd = OffsetDateTime.now();
        final AiUsageSpendSummary spendSummary = aiUsageLedgerService.summarizeSpend(userId, windowEnd.minusDays(30), windowEnd);

        return new DashboardStatsResponse(
            credits.balance(),
            credits.totalUsed(),
            (int) projectRepository.countByUserId(userId),
            (int) projectRepository.countByUserIdAndStatus(userId, ProjectStatus.PROCESSING),
            (int) projectRepository.countByUserIdAndStatus(userId, ProjectStatus.COMPLETED),
            (int) projectRepository.countByUserIdAndStatus(userId, ProjectStatus.FAILED),
            user.createdAt(),
            spendSummary.estimatedCostUsd(),
            spendSummary.reconciledCostUsd()
        );
    }
}
