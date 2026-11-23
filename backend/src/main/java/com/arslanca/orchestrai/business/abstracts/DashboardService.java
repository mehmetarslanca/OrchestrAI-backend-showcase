package com.arslanca.orchestrai.business.abstracts;
import com.arslanca.orchestrai.business.dtos.dashboard.DashboardStatsResponse;
import java.util.UUID;
public interface DashboardService {
    DashboardStatsResponse getDashboardStats(UUID userId);
}
