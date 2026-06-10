package com.api.finance.dashboard.controller;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.dashboard.dto.DashboardResponse;
import com.api.finance.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(dashboardService.getDashboard(caller));
    }
}