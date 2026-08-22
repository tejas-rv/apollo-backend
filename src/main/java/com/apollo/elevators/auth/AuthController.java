package com.apollo.elevators.auth;

import com.apollo.elevators.auth.dto.LoginRequest;
import com.apollo.elevators.auth.dto.LoginResponse;
import com.apollo.elevators.dto.PasswordResetRequest;
import com.apollo.elevators.security.JwtService;
import com.apollo.elevators.security.SecurityConfigService;
import com.apollo.elevators.service.PasswordResetService;
import com.apollo.elevators.user.User;
import com.apollo.elevators.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityConfigService securityConfigService;
    private final PasswordResetService passwordResetService;

    // PUBLIC — this is the only endpoint in this controller that should be reachable
    // without a token. Everything else in the API sits under /api/admin/** or
    // /api/engineer/** so the SecurityConfig matchers apply role checks correctly.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished — should not happen"));

        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());

        return ResponseEntity.ok(new LoginResponse(
                token,
                user.getUsername(),
                user.getRole().name(),
                securityConfigService.getJwtExpirationMs()
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @RequestBody PasswordResetRequest request) {

        passwordResetService.resetPassword(
                request.getUsername(),
                request.getNewPassword()
        );

        return ResponseEntity.ok("Password reset successfully");
    }
}
