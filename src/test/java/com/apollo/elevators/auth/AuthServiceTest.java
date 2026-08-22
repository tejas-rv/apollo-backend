package com.apollo.elevators.auth;

import com.apollo.elevators.auth.dto.LoginRequest;
import com.apollo.elevators.auth.dto.LoginResponse;
import com.apollo.elevators.auth.dto.RefreshTokenRequest;
import com.apollo.elevators.auth.dto.RefreshTokenResponse;
import com.apollo.elevators.common.Role;
import com.apollo.elevators.dto.PasswordResetRequest;
import com.apollo.elevators.repository.UserRepository;
import com.apollo.elevators.security.JwtService;
import com.apollo.elevators.security.SecurityConfigService;
import com.apollo.elevators.service.PasswordResetService;
import com.apollo.elevators.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityConfigService securityConfigService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void loginShouldReturnAccessAndRefreshTokens() {
        User user = User.builder()
                .id(1L)
                .username("admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("admin", "ADMIN")).thenReturn("access-token");
        when(refreshTokenService.issueToken(user))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", Instant.now().plusSeconds(60)));
        when(securityConfigService.getJwtExpirationMs()).thenReturn(86400000L);

        LoginResponse response = authService.login(new LoginRequest("admin", "password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo("ADMIN");
    }

    @Test
    void refreshShouldRotateRefreshTokenAndReturnNewPair() {
        User user = User.builder()
                .username("engineer")
                .role(Role.ENGINEER)
                .enabled(true)
                .build();

        when(refreshTokenService.rotate("old-refresh")).thenReturn(user);
        when(refreshTokenService.issueToken(user))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("new-refresh", Instant.now().plusSeconds(60)));
        when(jwtService.generateToken("engineer", "ENGINEER")).thenReturn("new-access");
        when(securityConfigService.getJwtExpirationMs()).thenReturn(86400000L);

        RefreshTokenResponse response = authService.refresh(new RefreshTokenRequest("old-refresh"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void resetPasswordShouldDelegateToPasswordResetServiceAndRevokeTokens() {
        User user = User.builder()
                .id(1L)
                .username("admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUsername("admin");
        request.setNewPassword("new-password");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        authService.resetPassword(request);

        verify(refreshTokenService).revokeAllActiveTokens(user);
        verify(passwordResetService).resetPassword("admin", "new-password");
    }
}
