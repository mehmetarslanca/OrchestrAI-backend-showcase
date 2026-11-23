package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiUsageLedgerService;
import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.UserManagementService;
import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;
import com.arslanca.orchestrai.business.dtos.dashboard.DashboardStatsResponse;
import com.arslanca.orchestrai.business.dtos.user.UserResponse;
import com.arslanca.orchestrai.business.usage.AiUsageSpendSummary;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardManagerTest {

    @Mock
    private CreditManagementService creditManagementService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserManagementService userManagementService;

    @Mock
    private AiUsageLedgerService aiUsageLedgerService;

    private DashboardManager dashboardManager;

    @BeforeEach
    void setUp() {
        dashboardManager = new DashboardManager(creditManagementService, projectRepository, userManagementService, aiUsageLedgerService);
    }

    @Test
    @DisplayName("getDashboardStats: should aggregate stats correctly")
    void shouldGetDashboardStats() {
        // Arrange
        UUID userId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        
        when(projectRepository.countByUserId(userId)).thenReturn(10L);
        when(projectRepository.countByUserIdAndStatus(userId, ProjectStatus.PROCESSING)).thenReturn(2L);
        when(projectRepository.countByUserIdAndStatus(userId, ProjectStatus.COMPLETED)).thenReturn(6L);
        when(projectRepository.countByUserIdAndStatus(userId, ProjectStatus.FAILED)).thenReturn(2L);

        when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(42, 5));
        
        when(userManagementService.getCurrentUser(userId)).thenReturn(
            new UserResponse(userId, "user", "email", "url", "GITHUB", "ROLE", createdAt)
        );
        when(aiUsageLedgerService.summarizeSpend(eq(userId), any(), any()))
                .thenReturn(new AiUsageSpendSummary(null, createdAt.minusDays(30), createdAt, null, null, 0, null));

        // Act
        DashboardStatsResponse stats = dashboardManager.getDashboardStats(userId);

        // Assert
        assertThat(stats.totalProjects()).isEqualTo(10);
        assertThat(stats.completedProjects()).isEqualTo(6);
        assertThat(stats.failedProjects()).isEqualTo(2);
        assertThat(stats.activeOrchestrations()).isEqualTo(2);
        assertThat(stats.creditBalance()).isEqualTo(42);
        assertThat(stats.totalCreditsUsed()).isEqualTo(5);
        assertThat(stats.memberSince()).isEqualTo(createdAt);
        assertThat(stats.last30DayEstimatedSpendUsd()).isEqualByComparingTo("0.00000000");
        assertThat(stats.last30DayReconciledSpendUsd()).isEqualByComparingTo("0.00000000");
    }
}
