package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.DashboardService;
import com.arslanca.orchestrai.business.dtos.dashboard.DashboardStatsResponse;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }


    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser) {
        final DashboardStatsResponse response = dashboardService.getDashboardStats(authenticatedUser.id());
        return ResponseEntity.ok(response);
    }
}
