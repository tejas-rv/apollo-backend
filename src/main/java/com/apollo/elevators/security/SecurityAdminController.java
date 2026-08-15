package com.apollo.elevators.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sits under /api/admin/** so SecurityConfig's hasRole("ADMIN") matcher
 * protects it automatically — a caller needs a valid ADMIN JWT to reach this.
 */
@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
public class SecurityAdminController {

    private final SecurityConfigService securityConfigService;

    /**
     * Call after rotating a value in the system_secret table (e.g. new JWT
     * secret) to reload it into memory without restarting the app.
     */
    @PostMapping("/config/refresh")
    public ResponseEntity<Void> refreshSecurityConfig() {
        securityConfigService.refresh();
        return ResponseEntity.noContent().build();
    }
}
